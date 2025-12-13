package dev.vuis.bfapi.cloud.unofficial;

import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.util.Util;
import java.io.IOException;
import java.time.Instant;
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
	private final Set<UUID> playerList;
	private final BfDataCache dataCache;

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
		log.info("requesting data for {} players", playerList.size());
		var listDataFutures = dataCache.playerData.get(playerList);
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

		List<BfPlayerData> playerDatas = listDataFutures.values().stream()
			.map(f -> f.join().value())
			.toList();

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
				d.getPrestigeLevel()
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
		int prestige
	) {
		public @NotNull JsonWriter serialize(@NotNull JsonWriter w) throws IOException {
			w.beginArray();

			w.value(Util.getBase64Uuid(uuid));
			w.value(username);
			w.value(exp);
			w.value(prestige);

			w.endArray();

			return w;
		}
	}
}
