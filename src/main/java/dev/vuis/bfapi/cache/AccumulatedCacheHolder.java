package dev.vuis.bfapi.cache;

import dev.vuis.bfapi.util.cache.TimedAccumulator;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public class AccumulatedCacheHolder<T> extends IdentifiableCacheHolder<T> implements AutoCloseable {
	private final Supplier<T> constructor;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final Map<UUID, TimedAccumulator<T>> accumulators = new ConcurrentHashMap<>();

	public AccumulatedCacheHolder(@NotNull Consumer<Set<UUID>> requester, @NotNull Supplier<T> constructor, @NotNull Duration lifetime) {
		super(requester, lifetime);
		this.constructor = constructor;
	}

	public void supply(UUID uuid, Consumer<T> mutator) {
		accumulators.computeIfAbsent(
			uuid, _ -> new TimedAccumulator<>(
				scheduler,
				250_000_000,
				data -> complete(uuid, data),
				constructor.get()
			)
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

	@Override
	public void close() {
		scheduler.shutdown();
	}
}
