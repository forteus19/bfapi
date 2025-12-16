package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.AbstractPlayerCloudData;
import com.boehmod.bflib.cloud.common.player.PlayerDataContext;
import com.boehmod.bflib.cloud.common.player.PlayerGroup;
import com.boehmod.bflib.cloud.common.player.PlayerRank;
import com.boehmod.bflib.cloud.common.player.PunishmentType;
import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.cloud.unofficial.UnofficialCloudData;
import dev.vuis.bfapi.util.Util;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class BfPlayerData extends AbstractPlayerCloudData<BfPlayerInventory> {
	private @Nullable PlayerGroup group;
	private int maxFriends = 0;
	private final Map<PunishmentType, Integer> pastPunishments = new EnumMap<>(PunishmentType.class);
	private final Map<PunishmentType, Integer> activePunishments = new EnumMap<>(PunishmentType.class);
	private boolean linkedDiscord = false;
	private boolean linkedPatreon = false;

	public BfPlayerData(@NotNull UUID uuid) {
		super(uuid);
	}

	public @NotNull JsonWriter serialize(@NotNull JsonWriter w, @Nullable UnofficialCloudData ucd) throws IOException {
		w.beginObject();

		w.name("uuid").value(getUUID().toString());
		w.name("username").value(getUsername());
		w.name("mood").value(getMood().orElse(null));
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
		w.name("clan").value(Util.ifNonNull(getClanId(), UUID::toString));
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
		w.name("group").value(Util.ifNonNull(group, PlayerGroup::getTag));
		w.name("max_friends").value(maxFriends);
		w.name("punishments").beginObject();
		w.name("past").beginObject();
		serializePunishmentMap(w, pastPunishments);
		w.endObject();
		w.name("active").beginObject();
		serializePunishmentMap(w, activePunishments);
		w.endObject();
		w.endObject();
		w.name("linked_discord").value(linkedDiscord);
		w.name("linked_patreon").value(linkedPatreon);
		if (ucd != null) {
			w.name("ucd").beginObject();
			w.name("exp_rank");
			int expRank = Util.indexOf(ucd.getPlayerExpLeaderboard(), p -> getUUID().equals(p.uuid()));
			if (expRank >= 0) {
				w.value(expRank + 1);
			} else {
				w.nullValue();
				if (getPrestigeLevel() > 0 || getExp() > Util.PRESTIGE_EXP) {
					log.warn("player {} ({}) has prestige exp but is not on EXP leaderboard", getUsername(), getUUID());
				}
			}
			w.endObject();
		}

		w.endObject();

		return w;
	}

	private void serializePunishmentMap(@NotNull JsonWriter w, Map<PunishmentType, Integer> map) throws IOException {
		int warnings = map.getOrDefault(PunishmentType.WARNING, 0);
		if (warnings > 0) {
			w.name("warning").value(warnings);
		}
		int mutes = map.getOrDefault(PunishmentType.MUTE, 0);
		if (mutes > 0) {
			w.name("mute").value(mutes);
		}
		int bans = map.getOrDefault(PunishmentType.BAN_MM, 0);
		if (bans > 0) {
			w.name("ban").value(bans);
		}
	}

	@Override
	public void read(@NotNull PlayerDataContext context, @NotNull ByteBuf buf) throws IOException {
		super.read(context, buf);
		maxFriends = buf.readInt();

		pastPunishments.clear();
		activePunishments.clear();
		for (PunishmentType type : PunishmentType.values()) {
			pastPunishments.put(type, buf.readInt());
			activePunishments.put(type, buf.readInt());
		}

		linkedDiscord = buf.readBoolean();
		linkedPatreon = buf.readBoolean();
		buf.readBoolean(); // mystery value
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
