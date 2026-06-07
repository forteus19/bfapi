package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.unofficial.UnofficialCloudData;
import dev.vuis.bfapi.data.BfApiConfig;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.AuthUtil;
import dev.vuis.bfapi.util.FriendScraper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
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
	private static final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor();
	private static @Nullable ScheduledFuture<?> cloudDataRefreshFuture = null;
	private static boolean startupUcdRefresh = false;

	private ApiMain() {
	}

	@SneakyThrows
	static void main() {
		BfApiConfig config = BfApiConfig.instance();

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
			authManager
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

		connection.dataCache.playerData.request(
			cloudData.playerScores().stream().map(ObjectIntImmutablePair::left).collect(Collectors.toUnmodifiableSet()),
			true
		);
		connection.dataCache.clanData.request(
			cloudData.clanScores().stream().map(ObjectIntImmutablePair::left).collect(Collectors.toUnmodifiableSet()),
			true
		);
	}
}
