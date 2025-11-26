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
import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.cloud.BfPlayerInventory;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.util.JsonUtil;
import dev.vuis.bfapi.util.Util;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Serialization {
	private Serialization() {
	}

	public static @NotNull JsonWriter challenge(@NotNull JsonWriter w, @NotNull Challenge challenge) throws IOException {
		w.beginObject();

		switch (challenge) {
			case KillCountChallenge killCount -> {
				w.name("type").value("kill_count");
				w.name("required").value(killCount.getAmountRequired());
				w.name("amount").value(killCount.getAmount());
			}
			case ItemKillChallenge itemKill -> {
				w.name("type").value("item_kill");
				w.name("item").value(itemKill.getItem().toString());
				w.name("required").value(itemKill.getAmountRequired());
				w.name("amount").value(itemKill.getAmount());
			}
			default -> throw new IllegalArgumentException("unknown challenge implementation");
		}
		w.name("reward").value(challenge.getExpReward());

		w.endObject();

		return w;
	}

	public static @NotNull JsonWriter clan(@NotNull JsonWriter w, @NotNull AbstractClanData clan, @Nullable BfDataCache dataCache) throws IOException {
		w.beginObject();

		w.name("uuid").value(clan.getClanId().toString());
		w.name("name").value(clan.getName());
		w.name("owner").beginObject();
		playerStub(w, dataCache, clan.getOwner()).endObject();
		w.name("members").beginArray();
		for (UUID member : clan.getMembers()) {
			w.beginObject();
			playerStub(w, dataCache, member);
			w.endObject();
		}
		w.endArray();

		w.endObject();

		return w;
	}

	public static @NotNull JsonWriter cloudItemStack(@NotNull JsonWriter w, @NotNull CloudItemStack stack, @NotNull CloudItem<?> item, boolean includeUuid, boolean includeDetails) throws IOException {
		w.beginObject();

		if (includeUuid) {
			w.name("uuid").value(stack.getUUID().toString());
		}
		w.name("id").value(stack.getItemId());
		if (includeDetails) {
			w.name("display_name").value(item.getDisplayName());
			w.name("rarity").value(item.getRarity().toString().toLowerCase());
			w.name("type").value(item.getItemType().toString().toLowerCase());
		}
		w.name("mint").value(stack.getMint());
		Optional<String> nameTag = stack.getNameTag();
		if (nameTag.isPresent()) {
			w.name("name_tag").value(nameTag.orElseThrow());
		}

		w.endObject();

		return w;
	}

	public static @NotNull JsonWriter matchData(@NotNull JsonWriter w, @NotNull MatchData matchData, @Nullable BfDataCache dataCache) throws IOException {
		w.beginObject();

		w.name("game").value(matchData.getGame().getId());
		w.name("map_name").value(matchData.getMapName());
		w.name("max_players").value(matchData.getMaxPlayerCount());
		w.name("accepting_players").value(matchData.isAcceptingPlayers());
		w.name("players").beginArray();
		for (UUID player : matchData.getPlayers()) {
			w.beginObject();
			playerStub(w, dataCache, player);
			w.endObject();
		}
		w.endArray();

		w.endObject();

		return w;
	}

	public static @NotNull JsonWriter playerInventory(@NotNull JsonWriter w, @NotNull BfPlayerInventory inventory, @NotNull CloudRegistry registry, boolean includeUuid, boolean includeDetails, @Nullable Consumer<JsonWriter> extra) throws IOException {
		w.beginObject();

		w.name("inventory").beginArray();
		for (CloudItemStack itemStack : inventory.getItems()) {
			CloudItem<?> item = itemStack.getCloudItem(registry);
			assert item != null;

			if (!item.isDefault()) {
				cloudItemStack(w, itemStack, item, includeUuid, includeDetails);
			}
		}
		w.endArray();

		if (extra != null) {
			extra.accept(w);
		}

		w.endObject();

		return w;
	}

	public static @NotNull JsonWriter playerStatus(@NotNull JsonWriter w, @NotNull PlayerStatus status, @Nullable BfDataCache dataCache, @Nullable Consumer<JsonWriter> extra) throws IOException {
		w.beginObject();

		w.name("online").value(status.getOnlineStatus().isOnline());
		w.name("party").value(status.getPartyStatus().toString().toLowerCase());
		w.name("server");
		JsonUtil.nullableValue(w, Util.ifNonNull(status.getServerOn(), UUID::toString));
		w.name("match");
		MatchData matchData = status.getMatchData();
		if (matchData != null) {
			matchData(w, matchData, dataCache);
		} else {
			w.nullValue();
		}

		if (extra != null) {
			extra.accept(w);
		}

		w.endObject();

		return w;
	}

	public static @NotNull JsonWriter playerStub(@NotNull JsonWriter w, @Nullable BfDataCache dataCache, @NotNull UUID uuid) throws IOException {
		namedStub(w, uuid, Util.getCachedPlayerName(dataCache, uuid));
		return w;
	}

	public static @NotNull JsonWriter clanStub(@NotNull JsonWriter w, @Nullable BfDataCache dataCache, @NotNull UUID uuid) throws IOException {
		namedStub(w, uuid, Util.getCachedClanName(dataCache, uuid));
		return w;
	}

	public static @NotNull JsonWriter namedStub(@NotNull JsonWriter w, @NotNull UUID uuid, @NotNull String name) throws IOException {
		w.name("uuid").value(uuid.toString());
		w.name("name").value(name);
		return w;
	}
}
