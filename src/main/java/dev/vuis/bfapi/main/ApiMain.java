package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedFriends;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import dev.vuis.bfapi.auth.MicrosoftAuth;
import dev.vuis.bfapi.auth.MinecraftAuth;
import dev.vuis.bfapi.auth.MsCodeWrapper;
import dev.vuis.bfapi.auth.XblAuth;
import dev.vuis.bfapi.auth.XstsAuth;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.unofficial.UnofficialCloudData;
import dev.vuis.bfapi.data.MinecraftProfile;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.QueryStringDecoder;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
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
	private static final String BF_PLAYER_LIST_FILE = Util.getEnvOrThrow("BF_PLAYER_LIST_FILE");
	private static final String BF_REFRESH_SECRET = Util.getEnvOrThrow("BF_REFRESH_SECRET");
	private static final boolean BF_SCRAPE_FRIENDS = Boolean.parseBoolean(Util.getEnvOrElse("BF_SCRAPE_FRIENDS", "false"));
	private static final int BF_SCRAPE_FRIENDS_DEPTH = Integer.parseInt(Util.getEnvOrElse("BF_SCRAPE_FRIENDS_DEPTH", "2"));

	private static final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor();
	private static @Nullable ScheduledFuture<?> cloudDataRefreshFuture = null;
	private static @Nullable CompletableFuture<Set<UUID>> friendScrapeFuture = null;

	private ApiMain() {
	}

	@SneakyThrows
	static void main() {
		String msClientSecret = null;
		if (MS_CLIENT_SECRET_FILE != null) {
			msClientSecret = Files.readString(Path.of(MS_CLIENT_SECRET_FILE));
		}
		Set<UUID> ucdPlayers = Arrays.stream(Files.readString(Path.of(BF_PLAYER_LIST_FILE)).split("\n"))
			.map(UUID::fromString).collect(Collectors.toSet());

		log.info("starting HTTP server");

		CompletableFuture<String> msCodeFuture = null;
		String msState = null;
		MsCodeWrapper msCodeWrapper = null;
		if (!MS_PASTE_REDIRECT) {
			msCodeFuture = new CompletableFuture<>();
			msState = MicrosoftAuth.randomState();
			msCodeWrapper = new MsCodeWrapper(msCodeFuture, msState);
		}

		BfApiInboundHandler inboundHandler = new BfApiInboundHandler(msCodeWrapper, BF_REFRESH_SECRET);
		startHttpServer(inboundHandler);

		MicrosoftAuth msAuth = new MicrosoftAuth(
			MS_CLIENT_ID,
			msClientSecret,
			MS_REDIRECT_HOST + (MS_PASTE_REDIRECT ? "" : BfApiInboundHandler.AUTH_CALLBACK_PATH)
		);

		log.info("microsoft auth URL: {}", msAuth.getAuthUri(MicrosoftAuth.XBOX_LIVE_SCOPE, msState));

		String msAuthorizationCode;
		if (MS_PASTE_REDIRECT) {
			log.info("paste redirected location:");
			String redirectInput = IO.readln();
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
		IO.readln();

		BfCloudPacketHandlers.register();
		if (BF_SCRAPE_FRIENDS) {
			BfCloudPacketHandlers.registerPacketHandler(PacketRequestedFriends.class, ApiMain::handleFriendScrapePacket);
		}

		BfConnection connection = new BfConnection(BF_CLOUD_ADDRESS, mcAuth, mcProfile, BF_VERSION, BF_VERSION_HASH, BF_HARDWARE_ID);
		connection.connect();

		UnofficialCloudData ucd = new UnofficialCloudData(ucdPlayers, connection.dataCache);

		inboundHandler.connection = connection;
		inboundHandler.ucd = ucd;

		connection.addStatusListener(status -> {
			switch (status) {
				case CONNECTED_VERIFIED -> {
					if (BF_SCRAPE_FRIENDS) {
						new Thread(() -> friendScraperThread(connection, ucdPlayers), "friend scraper").start();
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
		});
	}

	private static void startHttpServer(BfApiInboundHandler inboundHandler) {
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

	@SneakyThrows
	private static void friendScraperThread(BfConnection connection, Set<UUID> startFront) {
		Thread.sleep(2000);

		log.info("started friend scraper");

		MutableGraph<UUID> friendGraph = GraphBuilder.undirected().build();
		Set<UUID> scraped = new HashSet<>();
		Set<UUID> front = startFront;

		for (int depth = 1; depth <= BF_SCRAPE_FRIENDS_DEPTH; depth++) {
			int num = 0;
			Set<UUID> nextFront = new HashSet<>();

			for (UUID user : front) {
				num++;

				log.info("(depth {}, found {}) {}/{}", depth, friendGraph.nodes().size(), num, front.size());

				if (!scraped.add(user)) {
					log.info("skipped");
					continue;
				}

				friendScrapeFuture = new CompletableFuture<>();
				connection.sendPacket(new PacketClientRequest(
					EnumSet.noneOf(RequestType.class),
					ObjectList.of(Map.entry(user, EnumSet.of(RequestType.PLAYER_FRIENDS)))
				));

				Set<UUID> friends = friendScrapeFuture.join();
				friendScrapeFuture = null;

				for (UUID friend : friends) {
					friendGraph.putEdge(user, friend);

					if (!scraped.contains(friend)) {
						nextFront.add(friend);
					}
				}

				scraped.add(user);

				Thread.sleep(1000);
			}

			front = nextFront;
		}

		log.info("total players: {}", friendGraph.nodes().size());
		log.info("serializing");

		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Path.of("scraped_friends.txt")))) {
			for (UUID user : friendGraph.nodes()) {
				writer.println(user);
			}
		}

		log.info("done");
	}

	private static void handleFriendScrapePacket(PacketRequestedFriends packet, BfConnection connection) {
		if (friendScrapeFuture == null) {
			log.warn("unexpected PacketRequestedFriends (friend scrape mode)");
			return;
		}

		friendScrapeFuture.complete(packet.friends());
	}
}
