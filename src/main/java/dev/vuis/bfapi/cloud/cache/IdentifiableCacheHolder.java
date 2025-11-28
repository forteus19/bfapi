package dev.vuis.bfapi.cloud.cache;

import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.util.cache.ExpiryHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

public class IdentifiableCacheHolder<T> {
	protected final BfConnection connection;
	protected final RequestType requestType;
	protected final Duration lifetime;
	protected final Cache<UUID, ExpiryHolder<T>> cache;

	protected final Cache<UUID, CompletableFuture<ExpiryHolder<T>>> pendingCache = CacheBuilder.newBuilder()
		.expireAfterAccess(Duration.ofSeconds(15))
		.build();

	IdentifiableCacheHolder(BfConnection connection, RequestType requestType, Duration lifetime) {
		this.connection = connection;
		this.requestType = requestType;
		this.lifetime = lifetime;
		this.cache = CacheBuilder.newBuilder()
			.expireAfterWrite(lifetime)
			.build();
	}

	public CompletableFuture<ExpiryHolder<T>> get(UUID uuid) {
		ExpiryHolder<T> cached = cache.getIfPresent(uuid);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}

		CompletableFuture<ExpiryHolder<T>> pending = pendingCache.getIfPresent(uuid);
		if (pending != null) {
			return pending;
		}

		CompletableFuture<ExpiryHolder<T>> future = new CompletableFuture<>();
		pendingCache.put(uuid, future);

		request(uuid, true);

		return future;
	}

	public @Nullable ExpiryHolder<T> getIfPresent(UUID uuid) {
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

	public void complete(UUID uuid, T data) {
		ExpiryHolder<T> holder = new ExpiryHolder<>(data, Instant.now().plus(lifetime));

		CompletableFuture<ExpiryHolder<T>> future = pendingCache.getIfPresent(uuid);
		if (future != null) {
			future.complete(holder);
			pendingCache.invalidate(uuid);
		}

		cache.put(uuid, holder);
	}

	public void complete(UUID uuid, Exception e) {
		CompletableFuture<ExpiryHolder<T>> future = pendingCache.getIfPresent(uuid);
		if (future != null) {
			future.completeExceptionally(e);
			pendingCache.invalidate(uuid);
		}
	}
}
