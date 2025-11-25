package dev.vuis.bfapi.main;

import dev.vuis.bfapi.auth.MicrosoftAuth;
import dev.vuis.bfapi.auth.MinecraftAuth;
import dev.vuis.bfapi.auth.MsCodeWrapper;
import dev.vuis.bfapi.auth.XblAuth;
import dev.vuis.bfapi.auth.XstsAuth;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.data.MinecraftProfile;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ApiMain {
	private static final InetSocketAddress BF_CLOUD_ADDRESS = new InetSocketAddress("cloud.blockfrontmc.com", 1924);

	private static final int PORT = Integer.parseInt(Util.getEnvOrThrow("PORT"));
	private static final String MS_CLIENT_ID = Util.getEnvOrThrow("MS_CLIENT_ID");
	private static final String MS_CLIENT_SECRET_FILE = System.getenv("MS_CLIENT_SECRET_FILE");
	private static final String MS_REDIRECT_HOST = Util.getEnvOrThrow("MS_REDIRECT_HOST");
	private static final boolean MS_PASTE_REDIRECT = Boolean.parseBoolean(Util.getEnvOrThrow("MS_PASTE_REDIRECT"));
	private static final String BF_VERSION = Util.getEnvOrThrow("BF_VERSION");
	private static final String BF_VERSION_HASH = Util.getEnvOrThrow("BF_VERSION_HASH");
	private static final byte[] BF_HARDWARE_ID = Util.parseHexArray(Util.getEnvOrThrow("BF_HARDWARE_ID"));

	private static BfApiInboundHandler inboundHandler = null;
	private static final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor();
	private static @Nullable ScheduledFuture<?> refreshFuture = null;

	private ApiMain() {
	}

	@SneakyThrows
	public static void main(String[] args) {
		Scanner consoleScanner = new Scanner(System.in);

		log.info("starting HTTP server");

		CompletableFuture<String> msCodeFuture = null;
		String msState = null;
		MsCodeWrapper msCodeWrapper = null;
		if (!MS_PASTE_REDIRECT) {
			msCodeFuture = new CompletableFuture<>();
			msState = MicrosoftAuth.randomState();
			msCodeWrapper = new MsCodeWrapper(msCodeFuture, msState);
		}
		startHttpServer(msCodeWrapper);

		String msClientSecret = null;
		if (MS_CLIENT_SECRET_FILE != null) {
			msClientSecret = Files.readString(Path.of(MS_CLIENT_SECRET_FILE));
		}
		MicrosoftAuth msAuth = new MicrosoftAuth(
			MS_CLIENT_ID,
			msClientSecret,
			MS_REDIRECT_HOST + (MS_PASTE_REDIRECT ? "" : BfApiInboundHandler.AUTH_CALLBACK_PATH)
		);

		log.info("microsoft auth URL: {}", msAuth.getAuthUri(MicrosoftAuth.XBOX_LIVE_SCOPE, msState));

		String msAuthorizationCode;
		if (MS_PASTE_REDIRECT) {
			log.info("paste redirected location:");
			String redirectInput = consoleScanner.nextLine();
			msAuthorizationCode = parseRedirectResult(redirectInput);
		} else {
			msAuthorizationCode = msCodeFuture.get();
		}
		msAuth.redeemCode(msAuthorizationCode, false);

		XblAuth xblAuth = new XblAuth(msAuth);
		XstsAuth xstsAuth = new XstsAuth(xblAuth);
		MinecraftAuth mcAuth = new MinecraftAuth(xstsAuth);
		MinecraftProfile mcProfile = mcAuth.retrieveProfile();

		log.info("authenticated as {} ({})", mcProfile.username(), mcProfile.uuid());
		log.info("press enter to continue");
		consoleScanner.nextLine();

		BfCloudPacketHandlers.register();
		BfConnection connection = new BfConnection(mcAuth, mcProfile, BF_VERSION, BF_VERSION_HASH, BF_HARDWARE_ID);
		connection.connect(BF_CLOUD_ADDRESS);

		inboundHandler.connection = connection;

		connection.addStatusListener(status -> {
			switch (status) {
				case CONNECTED_VERIFIED -> {
					refreshFuture = refreshExecutor.scheduleAtFixedRate(
						() -> refreshScoreboardMembers(connection),
						10, 60, TimeUnit.SECONDS
					);
				}
				case CLOSED -> {
					if (refreshFuture != null) {
						refreshFuture.cancel(false);
						refreshFuture = null;
					}
				}
			}
		});
	}

	private static void startHttpServer(MsCodeWrapper msCodeWrapper) {
		inboundHandler = new BfApiInboundHandler(msCodeWrapper);
		ServerBootstrap bootstrap = new ServerBootstrap()
			.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()))
			.channel(NioServerSocketChannel.class)
			.childHandler(new BfApiChannelInitializer(inboundHandler));

		bootstrap.bind(PORT).syncUninterruptibly();
	}

	private static String parseRedirectResult(String uri) {
		QueryStringDecoder qs = new QueryStringDecoder(uri);
		if (!qs.parameters().containsKey("code")) {
			throw new IllegalArgumentException("uri does not have code query parameter");
		}
		return qs.parameters().get("code").getFirst();
	}

	private static void refreshScoreboardMembers(BfConnection connection) {
		if (!connection.isConnectedAndVerified()) {
			return;
		}

		BfCloudData cloudData;
		try {
			cloudData = connection.dataCache.cloudData.get().get(10, TimeUnit.SECONDS);
		} catch (InterruptedException | TimeoutException e) {
			return;
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}

		connection.dataCache.playerData.request(cloudData.playerScores().keySet(), true);
		connection.dataCache.clanData.request(cloudData.clanScores().keySet(), true);
	}
}
