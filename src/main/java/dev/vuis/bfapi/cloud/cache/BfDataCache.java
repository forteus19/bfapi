package dev.vuis.bfapi.cloud.cache;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.RequestType;
import com.google.common.cache.CacheBuilder;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.cloud.BfPlayerInventory;
import java.time.Duration;

public class BfDataCache {
	public final IdentifiableCacheHolder<AbstractClanData> clanData;
	public final IdentifiableCacheHolder<BfPlayerData> playerData;
	public final AccumulatedCacheHolder<BfPlayerInventory> playerInventory;

	public final SingletonCacheHolder<BfCloudData> cloudData;

	public BfDataCache(BfConnection connection) {
		clanData = new IdentifiableCacheHolder<>(
			connection, RequestType.CLAN_DATA,
			CacheBuilder.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(5))
				.build()
		);
		playerData = new IdentifiableCacheHolder<>(
			connection, RequestType.PLAYER_DATA,
			CacheBuilder.newBuilder()
				.expireAfterWrite(Duration.ofSeconds(90))
				.build()
		);
		playerInventory = new AccumulatedCacheHolder<>(
			connection, RequestType.PLAYER_INVENTORY, BfPlayerInventory::new,
			CacheBuilder.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(5))
				.build()
		);

		cloudData = new SingletonCacheHolder<>(
			connection, RequestType.CLOUD_STATS,
			Duration.ofSeconds(30)
		);
	}
}
