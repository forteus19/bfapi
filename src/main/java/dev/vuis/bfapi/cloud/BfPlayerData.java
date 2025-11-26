package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.AbstractPlayerCloudData;
import com.boehmod.bflib.cloud.common.player.PlayerGroup;
import com.boehmod.bflib.cloud.common.player.PlayerRank;
import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.util.JsonUtil;
import dev.vuis.bfapi.util.Util;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BfPlayerData extends AbstractPlayerCloudData<BfPlayerInventory> {
	private @Nullable PlayerGroup group;

	public BfPlayerData(@NotNull UUID uuid) {
		super(uuid);
	}

	public @NotNull JsonWriter serialize(@NotNull JsonWriter w) throws IOException {
		w.beginObject();

		w.name("uuid").value(getUUID().toString());
		w.name("username").value(getUsername());
		w.name("mood");
		JsonUtil.nullableValue(w, getMood().orElse(null));
		w.name("class_exp").beginArray();
		for (Int2IntMap.Entry entry : getClassExp().int2IntEntrySet()) {
			w.beginObject();
			w.name("id").value(entry.getIntKey());
			w.name("exp").value(entry.getIntValue());
			w.endObject();
		}
		w.endArray();
		w.name("exp").value(getExp());
		w.name("rank").value(PlayerRank.getRankFromEXP(getExp()).getTitle());
		w.name("trophies").value(getTrophies());
		w.name("prestige").value(getPrestigeLevel());
		w.name("match_karma").value(getMatchKarma());
		w.name("total_games").value(getTotalGames());
		w.name("time_played").value(getTimePlayed());
		w.name("bootcamp").value(hasCompletedBootcamp());
		w.name("clan");
		JsonUtil.nullableValue(w, Util.ifNonNull(getClanId(), UUID::toString));
		w.name("cases_opened").value(getCasesOpened());
		w.name("kills").value(getKills());
		w.name("assists").value(getAssists());
		w.name("infected_kills").value(getInfectedKills());
		w.name("vehicle_kills").value(getVehicleKills());
		w.name("bot_kills").value(getBotKills());
		w.name("deaths").value(getDeaths());
		w.name("back_stabs").value(getBackStabs());
		w.name("head_shots").value(getHeadShots());
		w.name("no_scopes").value(getNoScopes());
		w.name("first_bloods").value(getFirstBloods());
		w.name("fire_kills").value(getFireKills());
		w.name("highest_kill_streak").value(getKillStreak());
		w.name("highest_death_streak").value(getDeathStreak());
		w.name("infected_rounds_won").value(getInfectedRoundsWon());
		w.name("infected_matches_won").value(getInfectedMatchesWon());

		w.endObject();

		return w;
	}

	@Override
	public @Nullable AbstractClanData getClanData() {
		throw new AssertionError();
	}

	@Override
	protected @NotNull BfPlayerInventory createInventory() {
		// not used
		return null;
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
