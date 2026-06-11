package dev.vuis.bfapi.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.vuis.bfapi.util.cache.ExpiryHolder;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdentifiableCacheHolder<T> {
	protected final @NotNull Consumer<Set<UUID>> requester;
	protected final @NotNull Duration lifetime;
	private final @Nullable BiConsumer<UUID, T> completeListener;

	protected final @NotNull Cache<UUID, ExpiryHolder<T>> cache;

	protected final @NotNull Map<UUID, CompletableFuture<ExpiryHolder<T>>> pending = new ConcurrentHashMap<>();

	public IdentifiableCacheHolder(@NotNull Consumer<Set<UUID>> requester, @NotNull Duration lifetime) {
		this(requester, lifetime, null);
	}

	public IdentifiableCacheHolder(@NotNull Consumer<Set<UUID>> requester, @NotNull Duration lifetime, @Nullable BiConsumer<UUID, T> completeListener) {
		this.requester = requester;
		this.lifetime = lifetime;
		this.completeListener = completeListener;

		this.cache = CacheBuilder.newBuilder()
			.expireAfterWrite(lifetime)
			.build();
	}

	public CompletableFuture<ExpiryHolder<T>> get(UUID uuid) {
		var future = createFuture(uuid);

		if (future.right()) {
			request(uuid, false);
		}

		return future.left();
	}

	public Map<UUID, CompletableFuture<ExpiryHolder<T>>> get(Set<UUID> uuids) {
		Map<UUID, CompletableFuture<ExpiryHolder<T>>> futures = new Object2ObjectOpenHashMap<>(uuids.size());
		Set<UUID> requestUuids = new ObjectOpenHashSet<>();

		for (UUID uuid : uuids) {
			var future = createFuture(uuid);
			futures.put(uuid, future.left());
			if (future.right()) {
				requestUuids.add(uuid);
			}
		}

		request(requestUuids, false);

		return futures;
	}

	private Pair<CompletableFuture<ExpiryHolder<T>>, Boolean> createFuture(UUID uuid) {
		ExpiryHolder<T> cached = cache.getIfPresent(uuid);
		if (cached != null) {
			return Pair.of(CompletableFuture.completedFuture(cached), false);
		}

		CompletableFuture<ExpiryHolder<T>> newFuture = new CompletableFuture<>();
		newFuture.whenComplete((_, _) -> pending.remove(uuid));

		CompletableFuture<ExpiryHolder<T>> existing = pending.putIfAbsent(uuid, newFuture);
		if (existing != null) {
			return Pair.of(existing, false);
		}

		return Pair.of(newFuture, true);
	}

	public @Nullable ExpiryHolder<T> getIfPresent(UUID uuid) {
		return cache.getIfPresent(uuid);
	}

	public void request(UUID uuid, boolean override) {
		if (override || !cache.asMap().containsKey(uuid)) {
			requester.accept(Set.of(uuid));
		}
	}

	public void request(Set<UUID> uuids, boolean override) {
		if (uuids.isEmpty()) {
			return;
		}

		Map<UUID, ExpiryHolder<T>> cacheMap = cache.asMap();

		Set<UUID> filteredUuids = override ? uuids : uuids.stream().filter(u -> !cacheMap.containsKey(u)).collect(Collectors.toSet());
		if (!filteredUuids.isEmpty()) {
			requester.accept(filteredUuids);
		}
	}

	public void complete(UUID uuid, T data) {
		ExpiryHolder<T> holder = new ExpiryHolder<>(data, Instant.now().plus(lifetime));

		CompletableFuture<ExpiryHolder<T>> future = pending.get(uuid);
		if (future != null) {
			future.complete(holder);
		}

		cache.put(uuid, holder);

		if (completeListener != null) {
			completeListener.accept(uuid, data);
		}
	}

	public void complete(UUID uuid, Exception e) {
		CompletableFuture<ExpiryHolder<T>> future = pending.get(uuid);
		if (future != null) {
			future.completeExceptionally(e);
		}
	}

	public synchronized void purge() {
		cache.invalidateAll();
		for (CompletableFuture<ExpiryHolder<T>> future : pending.values()) {
			future.completeExceptionally(new PurgeException());
		}
		pending.clear();
	}
}
