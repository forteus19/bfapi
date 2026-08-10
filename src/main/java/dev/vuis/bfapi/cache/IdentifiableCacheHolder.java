package dev.vuis.bfapi.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.vuis.bfapi.util.cache.ExpiryHolder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectBooleanPair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdentifiableCacheHolder<K, V> {
	protected final @NotNull Consumer<Set<K>> requester;
	protected final @NotNull Duration lifetime;
	private final @Nullable BiConsumer<K, V> completeListener;

	protected final @NotNull Cache<K, ExpiryHolder<V>> cache;

	protected final @NotNull Map<K, CompletableFuture<ExpiryHolder<V>>> pending = new ConcurrentHashMap<>();

	public IdentifiableCacheHolder(@NotNull Consumer<Set<K>> requester, @NotNull Duration lifetime) {
		this(requester, lifetime, null);
	}

	public IdentifiableCacheHolder(@NotNull Consumer<Set<K>> requester, @NotNull Duration lifetime, @Nullable BiConsumer<K, V> completeListener) {
		this.requester = requester;
		this.lifetime = lifetime;
		this.completeListener = completeListener;

		this.cache = CacheBuilder.newBuilder()
			.expireAfterWrite(lifetime)
			.build();
	}

	public CompletableFuture<ExpiryHolder<V>> get(K key) {
		var future = createFuture(key);

		if (future.rightBoolean()) {
			request(key, false);
		}

		return future.left();
	}

	public Map<K, CompletableFuture<ExpiryHolder<V>>> get(Set<K> keys) {
		Map<K, CompletableFuture<ExpiryHolder<V>>> futures = new Object2ObjectOpenHashMap<>(keys.size());
		Set<K> requestKeys = new ObjectOpenHashSet<>();

		for (K key : keys) {
			var future = createFuture(key);

			futures.put(key, future.left());
			if (future.rightBoolean()) {
				requestKeys.add(key);
			}
		}

		request(requestKeys, false);

		return futures;
	}

	private ObjectBooleanPair<CompletableFuture<ExpiryHolder<V>>> createFuture(K key) {
		ExpiryHolder<V> cached = cache.getIfPresent(key);
		if (cached != null) {
			return ObjectBooleanPair.of(CompletableFuture.completedFuture(cached), false);
		}

		CompletableFuture<ExpiryHolder<V>> newFuture = new CompletableFuture<>();
		newFuture.whenComplete((_, _) -> pending.remove(key));

		CompletableFuture<ExpiryHolder<V>> existing = pending.putIfAbsent(key, newFuture);
		if (existing != null) {
			return ObjectBooleanPair.of(existing, false);
		}

		return ObjectBooleanPair.of(newFuture, true);
	}

	public @Nullable ExpiryHolder<V> getIfPresent(K key) {
		return cache.getIfPresent(key);
	}

	public void request(K key, boolean override) {
		if (override || !cache.asMap().containsKey(key)) {
			requester.accept(Set.of(key));
		}
	}

	public void request(Set<K> keys, boolean override) {
		if (keys.isEmpty()) {
			return;
		}

		Map<K, ExpiryHolder<V>> cacheMap = cache.asMap();

		Set<K> filteredKeys = override ? keys : keys.stream().filter(u -> !cacheMap.containsKey(u)).collect(Collectors.toSet());
		if (!filteredKeys.isEmpty()) {
			requester.accept(filteredKeys);
		}
	}

	public void complete(K key, V value) {
		ExpiryHolder<V> holder = new ExpiryHolder<>(value, Instant.now().plus(lifetime));

		CompletableFuture<ExpiryHolder<V>> future = pending.get(key);
		if (future != null) {
			future.complete(holder);
		}

		cache.put(key, holder);

		if (completeListener != null) {
			completeListener.accept(key, value);
		}
	}

	public void complete(K key, Exception e) {
		CompletableFuture<ExpiryHolder<V>> future = pending.get(key);
		if (future != null) {
			future.completeExceptionally(e);
		}
	}

	public synchronized void purge() {
		cache.invalidateAll();
		for (CompletableFuture<ExpiryHolder<V>> future : pending.values()) {
			future.completeExceptionally(new PurgeException());
		}
		pending.clear();
	}
}
