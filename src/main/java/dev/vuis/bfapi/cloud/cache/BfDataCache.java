package dev.vuis.bfapi.cloud.cache;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.common.player.status.PlayerStatus;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.cloud.BfPlayerInventory;
import java.time.Duration;

public class BfDataCache {
	public final IdentifiableCacheHolder<AbstractClanData> clanData;
	public final SingletonCacheHolder<BfCloudData> cloudData;
	public final IdentifiableCacheHolder<BfPlayerData> playerData;
	public final AccumulatedCacheHolder<BfPlayerInventory> playerInventory;
	public final IdentifiableCacheHolder<PlayerStatus> playerStatus;

	public BfDataCache(BfConnection connection) {
		clanData = new IdentifiableCacheHolder<>(
			connection, RequestType.CLAN_DATA,
			Duration.ofMinutes(5)
		);
		cloudData = new SingletonCacheHolder<>(
			connection, RequestType.CLOUD_STATS,
			Duration.ofSeconds(30)
		);
		playerData = new IdentifiableCacheHolder<>(
			connection, RequestType.PLAYER_DATA,
			Duration.ofSeconds(90)
		);
		playerInventory = new AccumulatedCacheHolder<>(
			connection, RequestType.PLAYER_INVENTORY, BfPlayerInventory::new,
			Duration.ofMinutes(5)
		);
		playerStatus = new IdentifiableCacheHolder<>(
			connection, RequestType.PLAYER_STATUS,
			Duration.ofSeconds(30)
		);
	}
}
