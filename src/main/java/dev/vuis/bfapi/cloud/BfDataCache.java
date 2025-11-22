package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BfDataCache {
	public final Cache<UUID, BfPlayerData> playerData = CacheBuilder.newBuilder()
		.expireAfterWrite(Duration.ofSeconds(30))
		.build();
	private final Map<UUID, CompletableFuture<BfPlayerData>> playerDataPending = new ConcurrentHashMap<>();

	private final BfConnection connection;

	public CompletableFuture<BfPlayerData> getPlayerData(UUID uuid) {
		BfPlayerData cached = playerData.getIfPresent(uuid);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}

		CompletableFuture<BfPlayerData> pending = new CompletableFuture<>();
		playerDataPending.put(uuid, pending);

		connection.sendPacket(new PacketClientRequest(
			EnumSet.noneOf(RequestType.class),
			ObjectList.of(Map.entry(uuid, EnumSet.of(RequestType.PLAYER_DATA)))
		));

		return pending;
	}

	void completePlayerData(UUID uuid, BfPlayerData data) {
		CompletableFuture<BfPlayerData> pending = playerDataPending.remove(uuid);
		if (pending != null) {
			pending.complete(data);
		}
		playerData.put(uuid, data);
	}

	void completePlayerData(UUID uuid, Exception e) {
		CompletableFuture<BfPlayerData> pending = playerDataPending.remove(uuid);
		if (pending != null) {
			pending.completeExceptionally(e);
		}
	}
}
