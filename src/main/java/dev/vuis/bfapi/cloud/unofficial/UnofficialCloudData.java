package dev.vuis.bfapi.cloud.unofficial;

import com.google.common.collect.Iterables;
import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.util.Util;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@RequiredArgsConstructor
public class UnofficialCloudData {
	private static final int REQUEST_CHUNK_SIZE = 128;
	private static final Duration REQUEST_PADDING_TIME = Duration.ofSeconds(3);

	private final Set<UUID> playerList;
	private final BfDataCache dataCache;
	private final boolean writeFilteredPlayers;

	private final AtomicBoolean refreshing = new AtomicBoolean(false);
	@Getter
	private Instant lastRefreshed = null;

	@Getter
	private List<Player> playerExpLeaderboard = List.of();
	@Getter
	private Set<UUID> clanList = Set.of();

	public boolean isRefreshing() {
		return refreshing.get();
	}

	public boolean startRefresh() {
		if (!refreshing.compareAndSet(false, true)) {
			log.warn("tried to start UCD refresh while still processing");
			return false;
		}

		log.info("starting UCD refresh");
		new Thread(this::refresh, "UCD refresh").start();
		return true;
	}

	private void refresh() {
		int numChunks = Math.ceilDiv(playerList.size(), REQUEST_CHUNK_SIZE);
		log.info("requesting data for {} players ({} chunks)", playerList.size(), numChunks);

		List<BfPlayerData> playerDatas = new ArrayList<>();

		Iterable<List<UUID>> uuidChunks = Iterables.partition(playerList, REQUEST_CHUNK_SIZE);
		int currentChunk = 0;

		for (List<UUID> uuidChunk : uuidChunks) {
			currentChunk++;
			log.info("getting chunk {}/{}", currentChunk, numChunks);

			var listDataFutures = dataCache.playerData.get(uuidChunk);
			try {
				CompletableFuture.allOf(listDataFutures.values().toArray(new CompletableFuture[0])).get(5, TimeUnit.MINUTES);
			} catch (InterruptedException | ExecutionException e) {
				log.error("ucd player list request failed", e);
				refreshing.set(false);
				return;
			} catch (TimeoutException e) {
				log.error("ucd player list request timed out");
				refreshing.set(false);
				return;
			}

			playerDatas.addAll(listDataFutures.values().stream()
				.map(f -> f.join().value())
				.filter(Util::hasPrestigeExp)
				.toList());

			int numFiltered = uuidChunk.size() - playerDatas.size();
			if (numFiltered > 0) {
				log.warn("{} players were filtered out", numFiltered);
			}

			if (currentChunk != numChunks) {
				try {
					Thread.sleep(REQUEST_PADDING_TIME);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}

		if (writeFilteredPlayers) {
			log.info("writing filtered players");

			try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Path.of("ucd_filtered_players.txt")))) {
				for (BfPlayerData playerData : playerDatas) {
					writer.println(playerData.getUUID());
				}
			} catch (IOException e) {
				log.error("failed to write filtered players", e);
			}
		}

		log.info("requesting cloud data");
		BfCloudData cloudData;
		try {
			cloudData = dataCache.cloudData.get().get(10, TimeUnit.SECONDS).value();
		} catch (InterruptedException | ExecutionException e) {
			log.error("ucd cloud data request failed", e);
			refreshing.set(false);
			return;
		} catch (TimeoutException e) {
			log.error("ucd cloud data request timed out");
			refreshing.set(false);
			return;
		}

		clanList = playerDatas.stream()
			.map(BfPlayerData::getClanId)
			.filter(Objects::nonNull)
			.collect(Collectors.toUnmodifiableSet());

		playerExpLeaderboard = playerDatas.stream()
			.sorted(Comparator.<BfPlayerData>comparingInt(d -> Util.getTotalExp(d.getPrestigeLevel(), d.getExp())).reversed())
			.map(d -> new Player(
				d.getUUID(),
				d.getUsername(),
				Util.getTotalExp(d.getPrestigeLevel(), d.getExp()),
				d.getPrestigeLevel(),
				Util.indexOf(cloudData.playerScores(), p -> p.left().equals(d.getUUID())) != -1
			))
			.toList();

		refreshing.set(false);
		lastRefreshed = Instant.now();
		log.info("refresh finished");
	}

	public @NotNull JsonWriter serializePlayerLeaderboard(@NotNull JsonWriter w, @NotNull List<Player> leaderboard) throws IOException {
		w.beginObject();

		serializeLastUpdated(w);
		w.name("leaderboard").beginArray();
		for (Player player : leaderboard) {
			player.serialize(w);
		}
		w.endArray();

		w.endObject();

		return w;
	}

	public @NotNull JsonWriter serializeClanList(@NotNull JsonWriter w) throws IOException {
		w.beginObject();

		serializeLastUpdated(w);
		w.name("clans").beginArray();
		for (UUID clan : clanList) {
			w.value(Util.getBase64Uuid(clan));
		}
		w.endArray();

		w.endObject();

		return w;
	}

	private @NotNull JsonWriter serializeLastUpdated(@NotNull JsonWriter w) throws IOException {
		w.name("last_updated").value(Util.ifNonNull(lastRefreshed, Instant::toString));
		return w;
	}

	public record Player(
		UUID uuid,
		String username,
		int exp,
		int prestige,
		boolean isActive
	) {
		public @NotNull JsonWriter serialize(@NotNull JsonWriter w) throws IOException {
			w.beginArray();

			w.value(Util.getBase64Uuid(uuid));
			w.value(username);
			w.value(exp);
			w.value(prestige);
			w.value(isActive ? 1 : 0);

			w.endArray();

			return w;
		}
	}
}
