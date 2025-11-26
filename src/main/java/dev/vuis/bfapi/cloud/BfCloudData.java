package dev.vuis.bfapi.cloud;

import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.data.Serialization;
import dev.vuis.bfapi.util.Util;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BfCloudData(
	int usersOnline,
	Map<String, Integer> gamePlayerCount,
	Instant scoreboardResetTime,
	Map<UUID, Integer> playerScores,
	Map<UUID, Integer> clanScores
) {
	public @NotNull JsonWriter serialize(@NotNull JsonWriter w, @Nullable BfDataCache dataCache) throws IOException {
		w.beginObject();

		w.name("players_online").value(usersOnline);
		w.name("game_player_count").beginObject();
		for (Map.Entry<String, Integer> entry : gamePlayerCount.entrySet()) {
			w.name(entry.getKey()).value(entry.getValue());
		}
		w.endObject();
		w.name("scoreboard_reset_time").value(scoreboardResetTime.toString());
		w.name("player_scores").beginArray();
		playerScores.entrySet().stream()
			.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
			.forEach(Util.unchecked(entry -> {
				w.beginObject();
				Serialization.playerStub(w, dataCache, entry.getKey());
				w.name("score").value(entry.getValue());
				w.endObject();
			}));
		w.endArray();
		w.name("clan_scores").beginArray();
		clanScores.entrySet().stream()
			.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
			.forEach(Util.unchecked(entry -> {
				w.beginObject();
				Serialization.clanStub(w, dataCache, entry.getKey());
				w.name("score").value(entry.getValue());
				w.endObject();
			}));
		w.endArray();

		w.endObject();

		return w;
	}
}
