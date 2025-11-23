package dev.vuis.bfapi.util;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.challenge.Challenge;
import com.boehmod.bflib.cloud.common.player.challenge.ItemKillChallenge;
import com.boehmod.bflib.cloud.common.player.challenge.KillCountChallenge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;

public final class Serialization {
	private Serialization() {
	}

	public static JsonObject challenge(Challenge challenge) {
		JsonObject root = new JsonObject();
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
		return root;
	}

	public static JsonObject clan(AbstractClanData clan) {
		JsonObject root = new JsonObject();
		root.addProperty("uuid", clan.getClanId().toString());
		root.addProperty("name", clan.getName());
		root.addProperty("owner", clan.getOwner().toString());
		root.add("members", Util.apply(new JsonArray(), members -> {
			for (UUID member : clan.getMembers()) {
				members.add(member.toString());
			}
		}));
		return root;
	}
}
