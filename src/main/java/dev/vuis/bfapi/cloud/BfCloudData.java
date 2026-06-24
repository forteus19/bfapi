package dev.vuis.bfapi.cloud;

import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.data.Serialization;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public record BfCloudData(
	int usersOnline,
	Map<String, Integer> gamePlayerCount,
	Instant scoreboardResetTime,
	List<ScoreEntry> playerScores,
	List<ScoreEntry> clanScores
) {
	public @NotNull JsonWriter serialize(@NotNull JsonWriter w, @NotNull BfDataCache dataCache) throws IOException {
		w.beginObject();

		w.name("players_online").value(usersOnline);
		w.name("game_player_count").beginObject();
		for (Map.Entry<String, Integer> entry : gamePlayerCount.entrySet()) {
			w.name(entry.getKey()).value(entry.getValue());
		}
		w.endObject();
		w.name("scoreboard_reset_time").value(scoreboardResetTime.toString());
		w.name("player_scores").beginArray();
		for (ScoreEntry playerScore : playerScores) {
			w.beginObject();
			Serialization.playerStub(w, dataCache, playerScore.uuid());
			w.name("score").value(playerScore.score());
			w.endObject();
		}
		w.endArray();
		w.name("clan_scores").beginArray();
		for (ScoreEntry clanScore : clanScores) {
			w.beginObject();
			Serialization.clanStub(w, dataCache, clanScore.uuid());
			w.name("score").value(clanScore.score());
			w.endObject();
		}
		w.endArray();

		w.endObject();

		return w;
	}

	public record ScoreEntry(
		UUID uuid,
		int score
	) {
		public static ScoreEntry of(Object2IntMap.Entry<UUID> mapEntry) {
			return new ScoreEntry(mapEntry.getKey(), mapEntry.getIntValue());
		}
	}
}
