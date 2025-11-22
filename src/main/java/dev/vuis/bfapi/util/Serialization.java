package dev.vuis.bfapi.util;

import com.boehmod.bflib.cloud.common.player.challenge.Challenge;
import com.boehmod.bflib.cloud.common.player.challenge.ItemKillChallenge;
import com.boehmod.bflib.cloud.common.player.challenge.KillCountChallenge;
import com.google.gson.JsonObject;

public final class Serialization {
	private Serialization() {
	}

	public static JsonObject challenge(Challenge challenge) {
		return Util.apply(new JsonObject(), root -> {
			switch (challenge) {
				case KillCountChallenge killCount -> {
					root.addProperty("type", "kill_count");
					root.addProperty("required", killCount.getAmountRequired());
					root.addProperty("amount", killCount.getAmount());
				}
				case ItemKillChallenge itemKill -> {
					root.addProperty("type", "item_kill");
					root.addProperty("item", itemKill.getItem().toString());
					root.addProperty("required", itemKill.getAmountRequired());
					root.addProperty("amount", itemKill.getAmount());
				}
				default -> throw new IllegalArgumentException("unknown challenge implementation");
			}
			root.addProperty("reward", challenge.getExpReward());
		});
	}
}
