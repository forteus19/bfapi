package dev.vuis.bfapi.cloud.cache;

import com.boehmod.bflib.cloud.common.RequestType;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.util.cache.TimedAccumulator;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public class AccumulatedCacheHolder<T> extends IdentifiableCacheHolder<T> {
	private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

	private final LoadingCache<UUID, TimedAccumulator<UUID, T>> accumulatorCache;

	AccumulatedCacheHolder(BfConnection connection, RequestType requestType, Supplier<T> constructor, Duration lifetime) {
		super(connection, requestType, lifetime);
		accumulatorCache = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofSeconds(1))
			.build(new CacheLoader<>() {
				@Override
				public @NotNull TimedAccumulator<UUID, T> load(@NotNull UUID key) {
					return new TimedAccumulator<>(executor, Duration.ofMillis(250), AccumulatedCacheHolder.this::complete, key, constructor.get());
				}
			});
	}

	public void supply(UUID uuid, Consumer<T> mutator) {
		accumulatorCache.getUnchecked(uuid).supply(mutator);
	}

	@Override
	public void complete(UUID uuid, T data) {
		super.complete(uuid, data);
		accumulatorCache.invalidate(uuid);
	}

	@Override
	public void complete(UUID uuid, Exception e) {
		super.complete(uuid, e);
		accumulatorCache.invalidate(uuid);
	}
}
