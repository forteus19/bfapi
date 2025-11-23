package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
				.forEach(entry -> playerScoresObj.add(Util.apply(new JsonObject(), playerScore -> {
					UUID uuid = entry.getKey();
					String name = "Unknown";
					if (dataCache != null) {
						BfPlayerData playerData = dataCache.playerData.getIfPresent(uuid);
						if (playerData != null) {
							name = playerData.getUsername();
						}
					}

					playerScore.addProperty("uuid", uuid.toString());
					playerScore.addProperty("name", name);
					playerScore.addProperty("score", entry.getValue());
				})));
		}));
		root.add("clan_scores", Util.apply(new JsonArray(), clanScoresObj -> {
			clanScores.entrySet().stream()
				.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
				.forEach(entry -> clanScoresObj.add(Util.apply(new JsonObject(), clanScore -> {
					UUID uuid = entry.getKey();
					String name = "Unknown";
					if (dataCache != null) {
						AbstractClanData clanData = dataCache.clanData.getIfPresent(uuid);
						if (clanData != null) {
							name = clanData.getName();
						}
					}

					clanScore.addProperty("uuid", uuid.toString());
					clanScore.addProperty("name", name);
					clanScore.addProperty("score", entry.getValue());
				})));
		}));
		return root;
	}
}
