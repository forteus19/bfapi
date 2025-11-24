package dev.vuis.bfapi.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.util.Serialization;
import dev.vuis.bfapi.util.Util;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record BfCloudData(
	int usersOnline,
	Map<String, Integer> gamePlayerCount,
	Instant scoreboardResetTime,
	Map<UUID, Integer> playerScores,
	Map<UUID, Integer> clanScores
) {
	public JsonObject serialize(@Nullable BfDataCache dataCache) {
		JsonObject root = new JsonObject();
		root.addProperty("players_online", usersOnline);
		root.add("game_player_count", Util.apply(new JsonObject(), gamePlayerCountObj -> {
			for (Map.Entry<String, Integer> entry : gamePlayerCount.entrySet()) {
				gamePlayerCountObj.addProperty(entry.getKey(), entry.getValue());
			}
		}));
		root.addProperty("scoreboard_reset_time", scoreboardResetTime.toString());
		root.add("player_scores", Util.apply(new JsonArray(), playerScoresObj -> {
			playerScores.entrySet().stream()
				.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
				.forEach(entry -> playerScoresObj.add(Util.apply(
					Serialization.getPlayerStub(entry.getKey(), dataCache),
					playerScore -> playerScore.addProperty("score", entry.getValue())
				)));
		}));
		root.add("clan_scores", Util.apply(new JsonArray(), clanScoresObj -> {
			clanScores.entrySet().stream()
				.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
				.forEach(entry -> clanScoresObj.add(Util.apply(
					Serialization.getClanStub(entry.getKey(), dataCache),
					clanScore -> clanScore.addProperty("score", entry.getValue())
				)));
		}));
		return root;
	}
}
