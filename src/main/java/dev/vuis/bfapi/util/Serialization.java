package dev.vuis.bfapi.util;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.challenge.Challenge;
import com.boehmod.bflib.cloud.common.player.challenge.ItemKillChallenge;
import com.boehmod.bflib.cloud.common.player.challenge.KillCountChallenge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.cloud.BfDataCache;
import dev.vuis.bfapi.cloud.BfPlayerData;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

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

	public static JsonObject clan(AbstractClanData clan, @Nullable BfDataCache dataCache) {
		JsonObject root = new JsonObject();
		root.addProperty("uuid", clan.getClanId().toString());
		root.addProperty("name", clan.getName());
		root.add("owner", getPlayerStub(clan.getOwner(), dataCache));
		root.add("members", Util.apply(new JsonArray(), members -> {
			for (UUID member : clan.getMembers()) {
				members.add(getPlayerStub(member, dataCache));
			}
		}));
		return root;
	}

	public static JsonObject getPlayerStub(UUID uuid, @Nullable BfDataCache dataCache) {
		JsonObject root = new JsonObject();

		String name = "Unknown";
		if (dataCache != null) {
			BfPlayerData playerData = dataCache.playerData.getIfPresent(uuid);
			if (playerData != null) {
				name = playerData.getUsername();
			}
		}

		root.addProperty("uuid", uuid.toString());
		root.addProperty("name", name);

		return root;
	}

	public static JsonObject getClanStub(UUID uuid, @Nullable BfDataCache dataCache) {
		JsonObject root = new JsonObject();

		String name = "Unknown";
		if (dataCache != null) {
			AbstractClanData clanData = dataCache.clanData.getIfPresent(uuid);
			if (clanData != null) {
				name = clanData.getName();
			}
		}

		root.addProperty("uuid", uuid.toString());
		root.addProperty("name", name);

		return root;
	}
}
