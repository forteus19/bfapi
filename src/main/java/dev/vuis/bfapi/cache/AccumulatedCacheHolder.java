package dev.vuis.bfapi.cache;

import dev.vuis.bfapi.util.cache.TimedAccumulator;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public class AccumulatedCacheHolder<K, V> extends IdentifiableCacheHolder<K, V> implements AutoCloseable {
	private final Supplier<V> constructor;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final Map<K, TimedAccumulator<V>> accumulators = new ConcurrentHashMap<>();

	public AccumulatedCacheHolder(@NotNull Consumer<Set<K>> requester, @NotNull Supplier<V> constructor, @NotNull Duration lifetime) {
		super(requester, lifetime);
		this.constructor = constructor;
	}

	public void supply(K key, Consumer<V> mutator) {
		accumulators.computeIfAbsent(
			key, _ -> new TimedAccumulator<>(
				scheduler,
				250_000_000,
				data -> complete(key, data),
				constructor.get()
			)
		).supply(mutator);
	}

	@Override
	public void complete(K key, V value) {
		super.complete(key, value);
		accumulators.remove(key);
	}

	@Override
	public void complete(K key, Exception e) {
		super.complete(key, e);
		accumulators.remove(key);
	}

	@Override
	public void close() {
		scheduler.shutdown();
	}
}
