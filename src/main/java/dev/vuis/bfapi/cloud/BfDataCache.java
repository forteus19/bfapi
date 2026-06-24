package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.common.player.status.PublicPlayerStatus;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.vuis.bfapi.cache.AccumulatedCacheHolder;
import dev.vuis.bfapi.cache.IdentifiableCacheHolder;
import dev.vuis.bfapi.cache.SingletonCacheHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public class BfDataCache implements AutoCloseable {
	public final IdentifiableCacheHolder<AbstractClanData> clanData;
	public final SingletonCacheHolder<BfCloudData> cloudStats;
	public final IdentifiableCacheHolder<Set<UUID>> playerInventoryDefaults;
	public final IdentifiableCacheHolder<BfPlayerData> playerData;
	public final AccumulatedCacheHolder<BfPlayerInventory> playerInventory;
	public final IdentifiableCacheHolder<BfPlayerInventory> inventoryMinimal;
	public final IdentifiableCacheHolder<PublicPlayerStatus> playerStatus;

	private final Cache<UUID, String> playerNameCache = CacheBuilder.newBuilder()
		.maximumSize(1024)
		.expireAfterWrite(Duration.ofHours(6))
		.build();
	private final Cache<UUID, String> clanNameCache = CacheBuilder.newBuilder()
		.maximumSize(1024)
		.expireAfterWrite(Duration.ofHours(6))
		.build();

	public BfDataCache(BfConnection connection) {
		clanData = new IdentifiableCacheHolder<>(
			identifiableRequester(connection, RequestType.CLAN_DATA),
			Duration.ofMinutes(5),
			this::handleClanDataCompleted
		);
		cloudStats = new SingletonCacheHolder<>(
			singletonRequester(connection, RequestType.CLOUD_STATS)
		);
		playerInventoryDefaults = new IdentifiableCacheHolder<>(
			identifiableRequester(connection, RequestType.PLAYER_INVENTORY_DEFAULTS),
			Duration.ofMinutes(5)
		);
		playerData = new IdentifiableCacheHolder<>(
			identifiableRequester(connection, RequestType.PLAYER_DATA),
			Duration.ofSeconds(90),
			this::handlePlayerDataCompleted
		);
		playerInventory = new AccumulatedCacheHolder<>(
			identifiableRequester(connection, RequestType.PLAYER_INVENTORY),
			BfPlayerInventory::new,
			Duration.ofMinutes(5)
		);
		inventoryMinimal = new IdentifiableCacheHolder<>(
			identifiableRequester(connection, RequestType.INVENTORY_MINIMAL),
			Duration.ofSeconds(90)
		);
		playerStatus = new IdentifiableCacheHolder<>(
			identifiableRequester(connection, RequestType.PLAYER_STATUS),
			Duration.ofSeconds(30)
		);
	}

	private static Consumer<Set<UUID>> identifiableRequester(BfConnection connection, RequestType requestType) {
		return uuids -> {
			ObjectList<Map.Entry<UUID, EnumSet<RequestType>>> requestEntries = new ObjectArrayList<>(uuids.size());

			for (UUID uuid : uuids) {
				requestEntries.add(Map.entry(uuid, EnumSet.of(requestType)));
			}

			connection.sendPacket(new PacketClientRequest(
				EnumSet.noneOf(RequestType.class),
				requestEntries
			));
		};
	}

	private static Runnable singletonRequester(BfConnection connection, RequestType requestType) {
		return () -> connection.sendPacket(new PacketClientRequest(
			EnumSet.of(requestType),
			ObjectList.of()
		));
	}

	private void handleClanDataCompleted(UUID uuid, AbstractClanData data) {
		clanNameCache.put(uuid, data.getName());
	}

	public @Nullable String getCachedClanName(UUID uuid) {
		return clanNameCache.getIfPresent(uuid);
	}

	private void handlePlayerDataCompleted(UUID uuid, BfPlayerData data) {
		playerNameCache.put(uuid, data.getUsername());
	}

	public @Nullable String getCachedPlayerName(UUID uuid) {
		return playerNameCache.getIfPresent(uuid);
	}

	public void purge() {
		clanData.purge();
		cloudStats.purge();
		playerInventoryDefaults.purge();
		playerData.purge();
		playerInventory.purge();
		inventoryMinimal.purge();
		playerStatus.purge();
	}

	@Override
	public void close() {
	}
}
