package dev.vuis.bfapi.cloud.unofficial;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.google.common.collect.Lists;
import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfDataCache;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.util.Util;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
@RequiredArgsConstructor
public final class UnofficialCloudData {
	private static final int REQUEST_CHUNK_SIZE = 6;
	private static final Duration REQUEST_PADDING_TIME = Duration.ofSeconds(1);

	private final Supplier<Set<UUID>> playerListLoader;
	private final BfDataCache dataCache;
	private final boolean writeFilteredPlayers;

	private final AtomicBoolean refreshing = new AtomicBoolean(false);
	private final AtomicReference<Instant> lastRefreshed = new AtomicReference<>();

	private final AtomicReference<Set<UUID>> playerList = new AtomicReference<>(Set.of());
	private final AtomicReference<Map<UUID, AbstractClanData>> clanLookup = new AtomicReference<>(Map.of());
	private final AtomicReference<List<Player>> playerExpLeaderboard = new AtomicReference<>(List.of());

	public boolean isEmpty() {
		return playerList.get().isEmpty();
	}

	public @Nullable Instant getLastRefreshed() {
		return lastRefreshed.get();
	}

	public List<Player> getPlayerExpLeaderboard() {
		return playerExpLeaderboard.get();
	}

	public boolean startRefresh() {
		playerList.set(playerListLoader.get());

		if (isEmpty()) {
			log.warn("skipping UCD refresh due to empty player list");
			return true;
		}

		if (!refreshing.compareAndSet(false, true)) {
			log.warn("tried to start UCD refresh while still processing");
			return false;
		}

		log.info("starting UCD refresh");
		new Thread(() -> {
			refresh();
			refreshing.set(false);
		}, "UCD refresh").start();
		return true;
	}

	private void refresh() {
		List<BfPlayerData> playerDatas = fetchPlayerDatas(playerList.get());

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
			cloudData = dataCache.cloudStats.get().get(10, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException e) {
			log.error("ucd cloud data request failed", e);
			return;
		} catch (TimeoutException e) {
			log.error("ucd cloud data request timed out");
			return;
		}

		playerExpLeaderboard.set(
			playerDatas.stream()
				.sorted(Comparator.<BfPlayerData>comparingInt(d -> Util.getTotalExp(d.getPrestigeLevel(), d.getExp())).reversed())
				.map(d -> new Player(
					d.getUUID(),
					d.getUsername(),
					Util.getTotalExp(d.getPrestigeLevel(), d.getExp()),
					d.getPrestigeLevel(),
					Util.indexOf(cloudData.playerScores(), p -> p.uuid().equals(d.getUUID())).isPresent(),
					d.getTimePlayed()
				))
				.toList()
		);

		Set<UUID> clans = playerDatas.stream()
			.map(BfPlayerData::getClanId)
			.filter(Objects::nonNull)
			.collect(Collectors.toUnmodifiableSet());

		List<AbstractClanData> clanDatas = fetchClanDatas(clans);

		clanLookup.set(
			clanDatas.stream()
				.collect(Collectors.toUnmodifiableMap(
					AbstractClanData::getClanId,
					clan -> clan
				))
		);

		lastRefreshed.set(Instant.now());
		log.info("refresh finished successfully");
	}

	private List<BfPlayerData> fetchPlayerDatas(Set<UUID> players) {
		List<List<UUID>> uuidChunks = Lists.partition(new ArrayList<>(players), REQUEST_CHUNK_SIZE);
		log.info("requesting data for {} players ({} chunks)", players.size(), uuidChunks.size());

		List<BfPlayerData> playerDatas = new ArrayList<>(players.size());

		for (int i = 0; i < uuidChunks.size(); i++) {
			try {
				Thread.sleep(REQUEST_PADDING_TIME);
			} catch (InterruptedException _) {
			}

			List<UUID> chunk = uuidChunks.get(i);
			log.info("getting chunk {}/{}", i + 1, uuidChunks.size());

			var listDataFutures = dataCache.playerData.get(new HashSet<>(chunk));
			try {
				CompletableFuture.allOf(listDataFutures.values().toArray(new CompletableFuture[0])).get(5, TimeUnit.MINUTES);
			} catch (InterruptedException | ExecutionException e) {
				log.error("ucd player request failed", e);
				continue;
			} catch (TimeoutException e) {
				log.error("ucd player request timed out");
				continue;
			}

			playerDatas.addAll(listDataFutures.values().stream()
				.map(f -> f.join().value())
				.filter(Util::hasPrestigeExp)
				.toList());

			int numFiltered = chunk.size() - playerDatas.size();
			if (numFiltered > 0) {
				log.warn("{} players were filtered out", numFiltered);
			}
		}

		return playerDatas;
	}

	private List<AbstractClanData> fetchClanDatas(Set<UUID> clans) {
		List<List<UUID>> uuidChunks = Lists.partition(new ArrayList<>(clans), REQUEST_CHUNK_SIZE);
		log.info("requesting data for {} clans ({} chunks)", clans.size(), uuidChunks.size());

		List<AbstractClanData> clanDatas = new ArrayList<>(clans.size());

		for (int i = 0; i < uuidChunks.size(); i++) {
			try {
				Thread.sleep(REQUEST_PADDING_TIME);
			} catch (InterruptedException _) {
			}

			List<UUID> chunk = uuidChunks.get(i);
			log.info("getting clan chunk {}/{}", i + 1, uuidChunks.size());

			var listDataFutures = dataCache.clanData.get(new HashSet<>(chunk));
			try {
				CompletableFuture.allOf(listDataFutures.values().toArray(new CompletableFuture[0])).get(5, TimeUnit.MINUTES);
			} catch (InterruptedException | ExecutionException e) {
				log.error("ucd clan request failed", e);
				continue;
			} catch (TimeoutException e) {
				log.error("ucd clan request timed out");
				continue;
			}

			clanDatas.addAll(listDataFutures.values().stream()
				.map(f -> f.join().value())
				.toList());
		}

		return clanDatas;
	}

	public @NotNull JsonWriter serializePlayerLeaderboard(@NotNull JsonWriter w) throws IOException {
		List<Player> leaderboard = playerExpLeaderboard.get();

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
		var clanLookupGet = clanLookup.get();

		w.beginObject();

		serializeLastUpdated(w);
		w.name("clans").beginArray();
		for (UUID clan : clanLookupGet.keySet()) {
			w.value(Util.getBase64Uuid(clan));
		}
		w.endArray();

		w.endObject();

		return w;
	}

	private @NotNull JsonWriter serializeLastUpdated(@NotNull JsonWriter w) throws IOException {
		Instant lastRefreshedGet = lastRefreshed.get();

		w.name("last_updated").value(Util.ifNonNull(lastRefreshedGet, Instant::toString));

		return w;
	}

	public record Player(
		UUID uuid,
		String username,
		int exp,
		int prestige,
		boolean isActive,
		int timePlayed
	) {
		public @NotNull JsonWriter serialize(@NotNull JsonWriter w) throws IOException {
			w.beginArray();

			w.value(Util.getBase64Uuid(uuid));
			w.value(username);
			w.value(exp);
			w.value(prestige);
			w.value(isActive ? 1 : 0);
			w.value(timePlayed);

			w.endArray();

			return w;
		}
	}
}
