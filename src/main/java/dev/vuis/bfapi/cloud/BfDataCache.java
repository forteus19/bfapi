package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.common.player.challenge.Challenge;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BfDataCache {
	public final IdentifiableHolder<AbstractClanData> clanData = new IdentifiableHolder<>(RequestType.CLAN_DATA);
	public final IdentifiableHolder<BfPlayerData> playerData = new IdentifiableHolder<>(RequestType.PLAYER_DATA);

	private final BfConnection connection;

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public class IdentifiableHolder<T> {
		private final Cache<UUID, T> cache = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofSeconds(60))
			.build();
		private final Map<UUID, CompletableFuture<T>> pending = new ConcurrentHashMap<>();

		private final RequestType requestType;

		public CompletableFuture<T> get(UUID uuid) {
			T cached = cache.getIfPresent(uuid);
			if (cached != null) {
				return CompletableFuture.completedFuture(cached);
			}
			if (pending.containsKey(uuid)) {
				return pending.get(uuid);
			}

			CompletableFuture<T> future = new CompletableFuture<>();
			pending.put(uuid, future);

			connection.sendPacket(new PacketClientRequest(
				EnumSet.noneOf(RequestType.class),
				ObjectList.of(Map.entry(uuid, EnumSet.of(requestType)))
			));

			return future;
		}

		void complete(UUID uuid, T data) {
			CompletableFuture<T> future = pending.remove(uuid);
			if (future != null) {
				future.complete(data);
			}
			cache.put(uuid, data);
		}

		void complete(UUID uuid, Exception e) {
			CompletableFuture<T> future = pending.remove(uuid);
			if (future != null) {
				future.completeExceptionally(e);
			}
		}
	}
}
