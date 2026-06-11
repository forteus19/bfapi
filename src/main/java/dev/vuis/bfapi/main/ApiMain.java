package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.unofficial.UnofficialCloudData;
import dev.vuis.bfapi.data.BfApiConfig;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.AuthUtil;
import dev.vuis.bfapi.util.FriendScraper;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ApiMain {
	private static boolean startupUcdRefresh = false;

	private static final ScheduledExecutorService REFRESH_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> cloudDataRefreshFuture = null;

	private ApiMain() {
	}

	@SneakyThrows
	static void main() {
		BfApiConfig config = BfApiConfig.instance();

		byte[] hardwareId = config.getBfHardwareId();
		if (hardwareId.length != 32) {
			log.warn("hardware ID is not 32 bytes (found {} bytes)", hardwareId.length);
			log.warn("press enter to continue");
			IO.readln();
		}

		HttpClient authHttpClient = MinecraftAuth.createHttpClient(config.getHttpUserAgent());
		JavaAuthManager authManager = AuthUtil.tryLoadAuthJson(authHttpClient, config.getTokensJsonPath());
		if (authManager == null) {
			authManager = AuthUtil.createAuthManagerFromLogin(authHttpClient);
		}

		log.info("retrieving profile");
		MinecraftProfile mcProfile = authManager.getMinecraftProfile().getUpToDate();

		log.info("authenticated as {} ({})", mcProfile.getName(), mcProfile.getId());
		log.info("press enter to continue");
		IO.readln();

		log.info("saving auth tokens");
		AuthUtil.saveAuthJson(authManager, config.getTokensJsonPath());

		log.info("starting HTTP server");
		BfApiInboundHandler inboundHandler = new BfApiInboundHandler(config.getBfUcdRefreshSecret());
		startHttpServer(inboundHandler, config.getApiPort());

		BfCloudPacketHandlers.registerPrimitive();
		BfCloudPacketHandlers.registerInfo();
		BfCloudPacketHandlers.registerData();
		if (config.isBfScrapeFriends()) {
			FriendScraper.registerPacketHandlers();
		}

		@Cleanup
		BfConnection connection = new BfConnection(
			config.getBfCloudAddress(),
			config.getBfVersion(),
			config.getBfVersionHash(),
			config.getBfHardwareId(),
			authManager,
			config.getHttpUserAgent(),
			createCommandUserRetriever(config)
		);
		connection.connect();

		UnofficialCloudData ucd = new UnofficialCloudData(() -> loadPlayerList(config.getBfPlayerListPath()), connection.dataCache, config.isBfUcdWriteFilteredPlayers());

		inboundHandler.connectionReference.set(connection);
		inboundHandler.ucdReference.set(ucd);
		connection.ucdReference.set(ucd);

		connection.addStatusListener((conn, status) -> onConnectionStatusChanged(conn, status, config, ucd));

		try {
			//noinspection InfiniteLoopStatement
			while (true) {
				String response = connection.handleCommand(IO.readln());
				if (response != null) {
					log.info(response);
				}
			}
		} catch (Exception _) {
		}
	}

	private static Function<String, UUID> createCommandUserRetriever(BfApiConfig config) throws IOException {
		Path commandUsersPath = config.getBfCommandUsersPath();

		if (commandUsersPath == null) {
			return _ -> null;
		}

		log.info("reading command users");

		JsonObject commandUsersRoot;
		try (BufferedReader reader = Files.newBufferedReader(commandUsersPath)) {
			commandUsersRoot = Util.COMPACT_GSON.fromJson(reader, JsonObject.class);
		}

		ImmutableMap.Builder<String, UUID> commandUsers = ImmutableMap.builderWithExpectedSize(commandUsersRoot.size());
		for (Map.Entry<String, JsonElement> entry : commandUsersRoot.entrySet()) {
			commandUsers.put(entry.getKey(), UUID.fromString(entry.getValue().getAsString()));
		}

		return commandUsers.buildOrThrow()::get;
	}

	static @NotNull Set<UUID> loadPlayerList(@Nullable Path playerListPath) {
		if (playerListPath != null) {
			try {
				log.info("loading player list");
				return Arrays.stream(Files.readString(playerListPath).split("\n")).map(UUID::fromString).collect(Collectors.toSet());
			} catch (Exception e) {
				log.error("failed to parse player list file");
				return Set.of();
			}
		} else {
			return Set.of();
		}
	}

	private static void startHttpServer(BfApiInboundHandler inboundHandler, int port) {
		ServerBootstrap bootstrap = new ServerBootstrap()
			.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()))
			.channel(NioServerSocketChannel.class)
			.childHandler(new BfApiChannelInitializer(inboundHandler));

		bootstrap.bind(port).syncUninterruptibly();
	}

	private static void onConnectionStatusChanged(BfConnection connection, ConnectionStatus status, BfApiConfig config, UnofficialCloudData ucd) {
		switch (status) {
			case CONNECTED_VERIFIED -> {
				if (config.isBfScrapeFriends()) {
					new Thread(
						() -> FriendScraper.start(connection, loadPlayerList(config.getBfPlayerListPath()), config.getBfScrapeFriendsDepth()),
						"friend scraper"
					).start();
				} else {
					if (config.isBfUcdRefreshOnStartup() && !startupUcdRefresh) {
						ucd.startRefresh();
						startupUcdRefresh = true;
					}

					cloudDataRefreshFuture = REFRESH_EXECUTOR.scheduleAtFixedRate(
						connection.dataCache.cloudData::request,
						0, 30, TimeUnit.SECONDS
					);
				}
			}
			case CLOSED -> {
				if (cloudDataRefreshFuture != null) {
					cloudDataRefreshFuture.cancel(true);
					cloudDataRefreshFuture = null;
				}
			}
		}
	}
}
