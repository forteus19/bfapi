package dev.vuis.bfapi.cloud;

import com.google.gson.JsonObject;
import dev.vuis.bfapi.util.Util;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BfCloudData(
	int usersOnline,
	Map<String, Integer> gamePlayerCount,
	Instant scoreboardResetTime,
	Map<UUID, Integer> playerScores,
	Map<UUID, Integer> clanScores
) {
	public JsonObject serialize() {
		JsonObject root = new JsonObject();
		root.addProperty("players_online", usersOnline);
		root.add("game_player_count", Util.apply(new JsonObject(), gamePlayerCountObj -> {
			for (Map.Entry<String, Integer> entry : gamePlayerCount.entrySet()) {
				gamePlayerCountObj.addProperty(entry.getKey(), entry.getValue());
			}
		}));
		root.addProperty("scoreboard_reset_time", scoreboardResetTime.toString());
		root.add("player_scores", Util.apply(new JsonObject(), playerScoresObj -> {
			playerScores.entrySet().stream()
				.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
				.forEach(entry -> playerScoresObj.addProperty(entry.getKey().toString(), entry.getValue()));
		}));
		root.add("clan_scores", Util.apply(new JsonObject(), clanScoresObj -> {
			clanScores.entrySet().stream()
				.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
				.forEach(entry -> clanScoresObj.addProperty(entry.getKey().toString(), entry.getValue()));
		}));
		return root;
	}
}
