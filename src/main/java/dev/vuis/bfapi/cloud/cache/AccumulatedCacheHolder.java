package dev.vuis.bfapi.cloud.cache;

import com.boehmod.bflib.cloud.common.RequestType;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.util.cache.TimedAccumulator;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AccumulatedCacheHolder<T> extends IdentifiableCacheHolder<T> {
	private final Supplier<T> constructor;

	private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
	private final Map<UUID, TimedAccumulator<UUID, T>> accumulators = new ConcurrentHashMap<>();

	AccumulatedCacheHolder(BfConnection connection, RequestType requestType, Supplier<T> constructor, Duration lifetime) {
		super(connection, requestType, lifetime);
		this.constructor = constructor;
	}

	public void supply(UUID uuid, Consumer<T> mutator) {
		accumulators.computeIfAbsent(
			uuid, k -> new TimedAccumulator<>(executor, Duration.ofMillis(250), AccumulatedCacheHolder.this::complete, k, constructor.get())
		).supply(mutator);
	}

	@Override
	public void complete(UUID uuid, T data) {
		super.complete(uuid, data);
		accumulators.remove(uuid);
	}

	@Override
	public void complete(UUID uuid, Exception e) {
		super.complete(uuid, e);
		accumulators.remove(uuid);
	}
}
