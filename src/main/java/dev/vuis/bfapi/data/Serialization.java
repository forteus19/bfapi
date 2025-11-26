package dev.vuis.bfapi.data;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.MatchData;
import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItemStack;
import com.boehmod.bflib.cloud.common.player.challenge.Challenge;
import com.boehmod.bflib.cloud.common.player.challenge.ItemKillChallenge;
import com.boehmod.bflib.cloud.common.player.challenge.KillCountChallenge;
import com.boehmod.bflib.cloud.common.player.status.PlayerStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.cloud.BfPlayerInventory;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.util.Util;
import java.util.Collection;
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

	public static JsonObject cloudItemStack(CloudItemStack stack, CloudRegistry registry, boolean includeUuid, boolean includeDetails) {
		CloudItem<?> item = stack.getCloudItem(registry);
		assert item != null;

		JsonObject root = new JsonObject();
		if (includeUuid) {
			root.addProperty("uuid", stack.getUUID().toString());
		}
		root.addProperty("id", stack.getItemId());
		if (includeDetails) {
			root.addProperty("display_name", item.getDisplayName());
			root.addProperty("rarity", item.getRarity().getName().toLowerCase());
			root.addProperty("type", item.getItemType().toString().toLowerCase());
		}
		root.addProperty("mint", stack.getMint());
		stack.getNameTag().ifPresent(nameTag -> root.addProperty("name_tag", nameTag));

		return root;
	}

	public static JsonObject matchData(MatchData matchData, @Nullable BfDataCache dataCache) {
		JsonObject root = new JsonObject();
		root.addProperty("uuid", matchData.getUUID().toString());
		root.add("players", Util.apply(new JsonArray(), players -> {
			for (UUID player : matchData.getPlayers()) {
				players.add(getPlayerStub(player, dataCache));
			}
		}));
		root.addProperty("map_name", matchData.getMapName());
		root.addProperty("game", matchData.getGame().getId());
		root.addProperty("max_players", matchData.getMaxPlayerCount());
		root.addProperty("accepting_players", matchData.isAcceptingPlayers());
		return root;
	}

	public static JsonObject playerInventory(BfPlayerInventory inventory, CloudRegistry registry, boolean includeUuid, boolean includeDetails) {
		Collection<CloudItemStack> itemsSorted = inventory.getItems();

		JsonObject root = new JsonObject();
		root.add("inventory", Util.apply(new JsonArray(), inventoryArray -> {
			for (CloudItemStack itemStack : itemsSorted) {
				CloudItem<?> item = itemStack.getCloudItem(registry);
				assert item != null;

				if (!item.isDefault()) {
					inventoryArray.add(cloudItemStack(itemStack, registry, includeUuid, includeDetails));
				}
			}
		}));

		return root;
	}

	public static JsonObject playerStatus(PlayerStatus status, @Nullable BfDataCache dataCache) {
		JsonObject root = new JsonObject();
		root.addProperty("online", status.getOnlineStatus().isOnline());
		root.addProperty("party", status.getPartyStatus().toString().toLowerCase());
		root.addProperty("server", Util.ifNonNull(status.getServerOn(), UUID::toString));
		root.add("match", Util.ifNonNull(status.getMatchData(), matchData -> Serialization.matchData(matchData, dataCache)));
		return root;
	}

	public static JsonObject getPlayerStub(UUID uuid, @Nullable BfDataCache dataCache) {
		String name = "Unknown";
		if (dataCache != null) {
			BfPlayerData playerData = dataCache.playerData.getIfPresent(uuid);
			if (playerData != null) {
				name = playerData.getUsername();
			}
		}

		return getPlayerStub(uuid, name);
	}

	public static JsonObject getPlayerStub(UUID uuid, String name) {
		JsonObject root = new JsonObject();

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
