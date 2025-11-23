package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.AbstractPlayerCloudData;
import com.boehmod.bflib.cloud.common.player.PlayerGroup;
import com.boehmod.bflib.cloud.common.player.PlayerRank;
import com.boehmod.bflib.cloud.common.player.challenge.Challenge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.util.Serialization;
import dev.vuis.bfapi.util.Util;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BfPlayerData extends AbstractPlayerCloudData<BfPlayerInventory> {
	private @Nullable PlayerGroup group;

	public BfPlayerData(@NotNull UUID uuid) {
		super(uuid);
	}

	public JsonObject serialize() {
		JsonObject root = new JsonObject();
		root.addProperty("uuid", getUUID().toString());
		root.addProperty("username", getUsername());
		root.addProperty("mood", getMood().orElse(null));
		root.add("class_exp", Util.apply(new JsonArray(), classExps -> {
			for (Int2IntMap.Entry entry : getClassExp().int2IntEntrySet()) {
				classExps.add(Util.apply(new JsonObject(), classExp -> {
					classExp.addProperty("id", entry.getIntKey());
					classExp.addProperty("exp", entry.getIntValue());
				}));
			}
		}));
		root.add("challenges", Util.apply(new JsonArray(), challenges -> {
			for (Challenge challenge : getChallenges()) {
				challenges.add(Serialization.challenge(challenge));
			}
		}));
		root.addProperty("exp", getExp());
		root.addProperty("rank", PlayerRank.getRankFromEXP(getExp()).getTitle());
		root.addProperty("trophies", getTrophies());
		root.addProperty("prestige", getPrestigeLevel());
		root.addProperty("match_karma", getMatchKarma());
		root.addProperty("total_games", getTotalGames());
		root.addProperty("time_played", getTimePlayed());
		root.addProperty("bootcamp", hasCompletedBootcamp());
		root.addProperty("clan", Util.ifNonNull(getClanId(), UUID::toString));
		root.addProperty("cases_opened", getCasesOpened());
		root.addProperty("kills", getKills());
		root.addProperty("assists", getAssists());
		root.addProperty("infected_kills", getInfectedKills());
		root.addProperty("vehicle_kills", getVehicleKills());
		root.addProperty("bot_kills", getBotKills());
		root.addProperty("deaths", getDeaths());
		root.addProperty("back_stabs", getBackStabs());
		root.addProperty("head_shots", getHeadShots());
		root.addProperty("no_scopes", getNoScopes());
		root.addProperty("first_bloods", getFirstBloods());
		root.addProperty("fire_kills", getFireKills());
		root.addProperty("highest_kill_streak", getKillStreak());
		root.addProperty("highest_death_streak", getDeathStreak());
		root.addProperty("infected_rounds_won", getInfectedRoundsWon());
		root.addProperty("infected_matches_won", getInfectedMatchesWon());
		return root;
	}

	@Override
	public @Nullable AbstractClanData getClanData() {
		throw new AssertionError();
	}

	@Override
	protected @NotNull BfPlayerInventory createInventory() {
		return new BfPlayerInventory(this);
	}

	@Override
	public @NotNull Optional<PlayerGroup> getGroup() {
		return Optional.ofNullable(group);
	}

	@Override
	public void setGroup(@Nullable PlayerGroup playerGroup) {
		group = playerGroup;
	}
}
