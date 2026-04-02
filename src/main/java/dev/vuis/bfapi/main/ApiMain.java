package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.unofficial.UnofficialCloudData;
import dev.vuis.bfapi.data.BfApiConfig;
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
	private static final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor();
	private static @Nullable ScheduledFuture<?> cloudDataRefreshFuture = null;

	private ApiMain() {
	}

	@SneakyThrows
	static void main() {
		BfApiConfig config = BfApiConfig.instance();

		Set<UUID> ucdPlayers;
		if (config.getBfPlayerListPath() != null) {
			ucdPlayers = parsePlayerListFile(config.getBfPlayerListPath());
		} else {
			ucdPlayers = Set.of();
		}

		HttpClient authHttpClient = MinecraftAuth.createHttpClient(config.getHttpUserAgent());
		JavaAuthManager authManager = tryLoadAuthJson(authHttpClient, config.getTokensJsonPath());
		if (authManager == null) {
			authManager = createAndLoginAuthManager(authHttpClient);
		}

		log.info("retrieving profile");
		MinecraftProfile mcProfile = authManager.getMinecraftProfile().getUpToDate();

		log.info("authenticated as {} ({})", mcProfile.getName(), mcProfile.getId());
		log.info("press enter to continue");
		IO.readln();

		log.info("saving auth tokens");
		saveAuthJson(authManager, config.getTokensJsonPath());

		log.info("starting HTTP server");
		BfApiInboundHandler inboundHandler = new BfApiInboundHandler(config.getBfUcdRefreshSecret());
		startHttpServer(inboundHandler, config.getApiPort());

		BfCloudPacketHandlers.register();
		if (config.isBfScrapeFriends()) {
			FriendScraper.registerPacketHandlers();
		}

		BfConnection connection = new BfConnection(
			config.getBfCloudAddress(),
			authManager,
			config.getBfVersion(),
			config.getBfVersionHash(),
			config.getBfHardwareId()
		);
		connection.connect();

		UnofficialCloudData ucd = new UnofficialCloudData(ucdPlayers, connection.dataCache, config.isBfUcdWriteFilteredPlayers());

		inboundHandler.connectionReference.set(connection);
		inboundHandler.ucdReference.set(ucd);

		connection.addStatusListener(status -> onConnectionStatusChanged(connection, status, config, ucd, ucdPlayers));
	}

	private static Set<UUID> parsePlayerListFile(Path playerListPath) {
		try {
			return Arrays.stream(Files.readString(playerListPath).split("\n")).map(UUID::fromString).collect(Collectors.toSet());
		} catch (Exception e) {
			return Set.of();
		}
	}

	private static JavaAuthManager tryLoadAuthJson(HttpClient authHttpClient, Path tokensJsonPath) {
		if (!Files.isRegularFile(tokensJsonPath)) {
			return null;
		}

		try (BufferedReader tokensReader = Files.newBufferedReader(tokensJsonPath)) {
			return JavaAuthManager.fromJson(authHttpClient, Util.PRETTY_GSON.fromJson(tokensReader, JsonObject.class));
		} catch (Exception e) {
			return null;
		}
	}

	private static void saveAuthJson(JavaAuthManager authManager, Path tokensJsonPath) throws IOException {
		JsonObject serializedTokens = JavaAuthManager.toJson(authManager);
		try (BufferedWriter tokensWriter = Files.newBufferedWriter(tokensJsonPath)) {
			Util.PRETTY_GSON.toJson(serializedTokens, tokensWriter);
		}
	}

	private static JavaAuthManager createAndLoginAuthManager(HttpClient authHttpClient) throws IOException, InterruptedException, TimeoutException {
		return JavaAuthManager.create(authHttpClient).login(
			DeviceCodeMsaAuthService::new,
			(Consumer<MsaDeviceCode>) code -> log.info("microsoft auth URL: {}", code.getDirectVerificationUri())
		);
	}

	private static void startHttpServer(BfApiInboundHandler inboundHandler, int port) {
		ServerBootstrap bootstrap = new ServerBootstrap()
			.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()))
			.channel(NioServerSocketChannel.class)
			.childHandler(new BfApiChannelInitializer(inboundHandler));

		bootstrap.bind(port).syncUninterruptibly();
	}

	private static void onConnectionStatusChanged(BfConnection connection, ConnectionStatus status, BfApiConfig config, UnofficialCloudData ucd, Set<UUID> ucdPlayers) {
		switch (status) {
			case CONNECTED_VERIFIED -> {
				if (config.isBfScrapeFriends()) {
					new Thread(() -> FriendScraper.start(connection, ucdPlayers, config.getBfScrapeFriendsDepth()), "friend scraper").start();
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
