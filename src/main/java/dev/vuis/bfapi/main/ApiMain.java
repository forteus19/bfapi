package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.unofficial.UnofficialCloudData;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.FriendScraper;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ApiMain {
	private static final InetSocketAddress BF_CLOUD_ADDRESS = new InetSocketAddress("cloud.blockfrontmc.com", 1924);

	private static final int PORT = Integer.parseInt(Util.getEnvOrThrow("PORT"));
	private static final Path TOKENS_JSON_PATH = Path.of(Util.getEnvOrElse("TOKENS_JSON_PATH", "bfapi_auth_tokens.json"));
	private static final String BF_VERSION = Util.getEnvOrThrow("BF_VERSION");
	private static final String BF_VERSION_HASH = Util.getEnvOrThrow("BF_VERSION_HASH");
	private static final byte[] BF_HARDWARE_ID = Util.parseHexArray(Util.getEnvOrThrow("BF_HARDWARE_ID"));
	private static final String BF_PLAYER_LIST_FILE = Util.getEnvOrThrow("BF_PLAYER_LIST_FILE");
	private static final String BF_UCD_REFRESH_SECRET = Util.getEnvOrThrow("BF_UCD_REFRESH_SECRET");
	private static final boolean BF_UCD_WRITE_FILTERED_PLAYERS = Boolean.parseBoolean(Util.getEnvOrElse("BF_UCD_WRITE_FILTERED_PLAYERS", "false"));
	private static final boolean BF_SCRAPE_FRIENDS = Boolean.parseBoolean(Util.getEnvOrElse("BF_SCRAPE_FRIENDS", "false"));
	private static final int BF_SCRAPE_FRIENDS_DEPTH = Integer.parseInt(Util.getEnvOrElse("BF_SCRAPE_FRIENDS_DEPTH", "2"));

	private static final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor();
	private static @Nullable ScheduledFuture<?> cloudDataRefreshFuture = null;

	private ApiMain() {
	}

	@SneakyThrows
	static void main() {
		Set<UUID> ucdPlayers = Arrays.stream(Files.readString(Path.of(BF_PLAYER_LIST_FILE)).split("\n"))
			.map(UUID::fromString).collect(Collectors.toSet());

		HttpClient authHttpClient = MinecraftAuth.createHttpClient("bfapi/1.0-SNAPSHOT");
		JavaAuthManager authManager = tryLoadAuthJson(authHttpClient);
		if (authManager == null) {
			authManager = JavaAuthManager.create(authHttpClient)
				.login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>) code -> log.info("microsoft auth URL: {}", code.getDirectVerificationUri()));
		}

		log.info("retrieving profile");
		MinecraftProfile mcProfile = authManager.getMinecraftProfile().getUpToDate();

		log.info("authenticated as {} ({})", mcProfile.getName(), mcProfile.getId());
		log.info("press enter to continue");
		IO.readln();

		log.info("saving auth tokens");
		saveAuthJson(authManager);

		log.info("starting HTTP server");
		BfApiInboundHandler inboundHandler = new BfApiInboundHandler(BF_UCD_REFRESH_SECRET);
		startHttpServer(inboundHandler);

		BfCloudPacketHandlers.register();
		if (BF_SCRAPE_FRIENDS) {
			FriendScraper.registerPacketHandlers();
		}

		BfConnection connection = new BfConnection(BF_CLOUD_ADDRESS, authManager, BF_VERSION, BF_VERSION_HASH, BF_HARDWARE_ID);
		connection.connect();

		UnofficialCloudData ucd = new UnofficialCloudData(ucdPlayers, connection.dataCache, BF_UCD_WRITE_FILTERED_PLAYERS);

		inboundHandler.connection = connection;
		inboundHandler.ucd = ucd;

		connection.addStatusListener(status -> onConnectionStatusChanged(connection, status, ucd, ucdPlayers));
	}

	private static JavaAuthManager tryLoadAuthJson(HttpClient authHttpClient) {
		if (!Files.isRegularFile(TOKENS_JSON_PATH)) {
			return null;
		}
		try (BufferedReader tokensReader = Files.newBufferedReader(TOKENS_JSON_PATH)) {
			return JavaAuthManager.fromJson(authHttpClient, Util.PRETTY_GSON.fromJson(tokensReader, JsonObject.class));
		} catch (Exception e) {
			return null;
		}
	}

	private static void saveAuthJson(JavaAuthManager authManager) throws IOException {
		JsonObject serializedTokens = JavaAuthManager.toJson(authManager);
		try (BufferedWriter tokensWriter = Files.newBufferedWriter(TOKENS_JSON_PATH)) {
			Util.PRETTY_GSON.toJson(serializedTokens, tokensWriter);
		}
	}

	private static void startHttpServer(BfApiInboundHandler inboundHandler) {
		ServerBootstrap bootstrap = new ServerBootstrap()
			.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()))
			.channel(NioServerSocketChannel.class)
			.childHandler(new BfApiChannelInitializer(inboundHandler));

		bootstrap.bind(PORT).syncUninterruptibly();
	}

	private static void onConnectionStatusChanged(BfConnection connection, ConnectionStatus status, UnofficialCloudData ucd, Set<UUID> ucdPlayers) {
		switch (status) {
			case CONNECTED_VERIFIED -> {
				if (BF_SCRAPE_FRIENDS) {
					new Thread(() -> FriendScraper.start(connection, ucdPlayers, BF_SCRAPE_FRIENDS_DEPTH), "friend scraper").start();
				} else {
					ucd.startRefresh();

					cloudDataRefreshFuture = refreshExecutor.scheduleAtFixedRate(
						() -> refreshCloudData(connection),
						0, 60, TimeUnit.SECONDS
					);
				}
			}
			case CLOSED -> {
				if (cloudDataRefreshFuture != null) {
					cloudDataRefreshFuture.cancel(false);
					cloudDataRefreshFuture = null;
				}
			}
		}
	}

	private static void refreshCloudData(BfConnection connection) {
		if (!connection.isConnectedAndVerified()) {
			return;
		}

		BfCloudData cloudData;
		try {
			cloudData = connection.dataCache.cloudData.get().get(10, TimeUnit.SECONDS).value();
		} catch (InterruptedException | TimeoutException e) {
			return;
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}

		connection.dataCache.playerData.request(cloudData.playerScores().stream().map(ObjectIntImmutablePair::left).collect(Collectors.toUnmodifiableSet()), true);
		connection.dataCache.clanData.request(cloudData.clanScores().stream().map(ObjectIntImmutablePair::left).collect(Collectors.toUnmodifiableSet()), true);
	}
}
