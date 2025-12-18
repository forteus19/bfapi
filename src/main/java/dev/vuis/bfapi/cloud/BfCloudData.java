package dev.vuis.bfapi.cloud;

import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.data.Serialization;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BfCloudData(
	int usersOnline,
	Map<String, Integer> gamePlayerCount,
	Instant scoreboardResetTime,
	List<ObjectIntImmutablePair<UUID>> playerScores,
	List<ObjectIntImmutablePair<UUID>> clanScores
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
		for (Pair<UUID, Integer> playerScore : playerScores) {
			w.beginObject();
			Serialization.playerStub(w, dataCache, playerScore.left());
			w.name("score").value(playerScore.right());
			w.endObject();
		}
		w.endArray();
		w.name("clan_scores").beginArray();
		for (Pair<UUID, Integer> clanScore : clanScores) {
			w.beginObject();
			Serialization.clanStub(w, dataCache, clanScore.left());
			w.name("score").value(clanScore.right());
			w.endObject();
		}
		w.endArray();

		w.endObject();

		return w;
	}
}
