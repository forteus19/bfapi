package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
public class BfDataCache {
	public final IdentifiableHolder<AbstractClanData> clanData = new IdentifiableHolder<>(
		RequestType.CLAN_DATA,
		CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(5))
			.build()
	);
	public final IdentifiableHolder<BfPlayerData> playerData = new IdentifiableHolder<>(
		RequestType.PLAYER_DATA,
		CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofSeconds(90))
			.build()
	);

	public final ConstantHolder<BfCloudData> cloudData = new ConstantHolder<>(
		RequestType.CLOUD_STATS,
		Duration.ofSeconds(30)
	);

	private final BfConnection connection;

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public class IdentifiableHolder<T> {
		private final RequestType requestType;
		private final Cache<UUID, T> cache;

		private final Cache<UUID, CompletableFuture<T>> pendingCache = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofSeconds(15))
			.build();

		public CompletableFuture<T> get(UUID uuid) {
			T cached = cache.getIfPresent(uuid);
			if (cached != null) {
				return CompletableFuture.completedFuture(cached);
			}

			CompletableFuture<T> pending = pendingCache.getIfPresent(uuid);
			if (pending != null) {
				return pending;
			}

			CompletableFuture<T> future = new CompletableFuture<>();
			pendingCache.put(uuid, future);

			request(uuid, true);

			return future;
		}

		public @Nullable T getIfPresent(UUID uuid) {
			return cache.getIfPresent(uuid);
		}

		public void request(UUID uuid, boolean override) {
			if (!override && cache.asMap().containsKey(uuid)) {
				return;
			}

			connection.sendPacket(new PacketClientRequest(
				EnumSet.noneOf(RequestType.class),
				ObjectList.of(Map.entry(uuid, EnumSet.of(requestType)))
			));
		}

		public void request(Collection<UUID> uuids, boolean override) {
			ObjectList<Map.Entry<UUID, EnumSet<RequestType>>> requestEntries = new ObjectArrayList<>();

			for (UUID uuid : uuids) {
				if (!override && cache.asMap().containsKey(uuid)) {
					continue;
				}

				requestEntries.add(Map.entry(uuid, EnumSet.of(requestType)));
			}

			if (requestEntries.isEmpty()) {
				return;
			}

			connection.sendPacket(new PacketClientRequest(
				EnumSet.noneOf(RequestType.class),
				requestEntries
			));
		}

		void complete(UUID uuid, T data) {
			CompletableFuture<T> future = pendingCache.getIfPresent(uuid);
			if (future != null) {
				future.complete(data);
				pendingCache.invalidate(uuid);
			}
			cache.put(uuid, data);
		}

		void complete(UUID uuid, Exception e) {
			CompletableFuture<T> future = pendingCache.getIfPresent(uuid);
			if (future != null) {
				future.completeExceptionally(e);
				pendingCache.invalidate(uuid);
			}
		}
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public class ConstantHolder<T> {
		private final RequestType requestType;
		private final Duration lifetime;

		private final AtomicReference<T> currentValue = new AtomicReference<>();
		private final AtomicReference<Instant> lastUpdated = new AtomicReference<>();
		private final AtomicReference<CompletableFuture<T>> pending = new AtomicReference<>();

		public CompletableFuture<T> get() {
			T value = currentValue.get();
			Instant lastUpdatedNow = lastUpdated.get();
			if (value != null && lastUpdatedNow != null && Duration.between(lastUpdatedNow, Instant.now()).compareTo(lifetime) < 0) {
				return CompletableFuture.completedFuture(value);
			}

			CompletableFuture<T> pendingNow = pending.get();
			if (pendingNow != null) {
				return pendingNow;
			}

			CompletableFuture<T> future = new CompletableFuture<>();
			if (pending.compareAndSet(null, future)) {
				request();

				return future;
			} else {
				return pending.get();
			}
		}

		private void request() {
			connection.sendPacket(new PacketClientRequest(
				EnumSet.of(requestType),
				ObjectList.of()
			));
		}

		void complete(T data) {
			CompletableFuture<T> pendingNow = pending.get();
			if (pendingNow != null) {
				pendingNow.complete(data);
			}
			currentValue.set(data);
			lastUpdated.set(Instant.now());
		}

		void complete(Exception e) {
			CompletableFuture<T> pendingNow = pending.get();
			if (pendingNow != null) {
				pendingNow.completeExceptionally(e);
			}
		}
	}
}
