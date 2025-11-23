package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BfDataCache {
	public final IdentifiableHolder<AbstractClanData> clanData = new IdentifiableHolder<>(RequestType.CLAN_DATA, Duration.ofMinutes(5));
	public final IdentifiableHolder<BfPlayerData> playerData = new IdentifiableHolder<>(RequestType.PLAYER_DATA, Duration.ofSeconds(60));

	public final ConstantHolder<BfCloudData> cloudData = new ConstantHolder<>(RequestType.CLOUD_STATS, Duration.ofSeconds(60));

	private final BfConnection connection;

	public class IdentifiableHolder<T> {
		private final RequestType requestType;

		private final Cache<UUID, T> cache;
		private final Map<UUID, CompletableFuture<T>> pending = new ConcurrentHashMap<>();

		private IdentifiableHolder(RequestType requestType, Duration lifetime) {
			this.requestType = requestType;
			cache = CacheBuilder.newBuilder()
				.expireAfterWrite(lifetime)
				.build();
		}

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

			sendRequest(uuid);

			return future;
		}

		private void sendRequest(UUID uuid) {
			connection.sendPacket(new PacketClientRequest(
				EnumSet.noneOf(RequestType.class),
				ObjectList.of(Map.entry(uuid, EnumSet.of(requestType)))
			));
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
				sendRequest();

				return future;
			} else {
				return pending.get();
			}
		}

		private void sendRequest() {
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
