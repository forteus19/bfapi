package dev.vuis.bfapi.cloud.cache;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TimedAccumulator<K, T> {
	private final ScheduledExecutorService scheduler;
	private final Duration timeout;
	private final BiConsumer<K, T> completer;
	private final K key;
	private final T instance;

	private ScheduledFuture<?> timeoutFuture = null;

	public synchronized void supply(Consumer<T> mutator) {
		mutator.accept(instance);

		if (timeoutFuture != null) {
			timeoutFuture.cancel(false);
		}

		timeoutFuture = scheduler.schedule(() -> completer.accept(key, instance), timeout.getNano(), TimeUnit.NANOSECONDS);
	}
}
