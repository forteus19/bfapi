package dev.vuis.bfapi.util;

import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedFriends;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class FriendScraper {
	private static @Nullable CompletableFuture<Set<UUID>> friendScrapeFuture = null;

	private FriendScraper() {
	}

	@SneakyThrows
	public static void start(BfConnection connection, Set<UUID> startFront, int maxDepth) {
		Thread.sleep(2000);

		log.info("started friend scraper");

		MutableGraph<UUID> friendGraph = GraphBuilder.undirected().build();
		Set<UUID> scraped = new HashSet<>();
		Set<UUID> front = startFront;

		for (int depth = 1; depth <= maxDepth; depth++) {
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

	public static void registerPacketHandlers() {
		BfCloudPacketHandlers.registerPacketHandler(PacketRequestedFriends.class, FriendScraper::handleFriendsPacket);
	}

	private static void handleFriendsPacket(PacketRequestedFriends packet, BfConnection connection) {
		if (friendScrapeFuture == null) {
			log.warn("unexpected PacketRequestedFriends (friend scrape mode)");
			return;
		}

		friendScrapeFuture.complete(packet.friends());
	}
}
