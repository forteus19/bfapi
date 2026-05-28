package dev.vuis.bfapi.util.cache;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TimedAccumulator<T> {
	private final ScheduledExecutorService scheduler;
	private final long timeoutNanos;
	private final Consumer<T> completer;
	private final T instance;

	private ScheduledFuture<?> timeoutFuture = null;
	private boolean completed = false;

	public synchronized void supply(Consumer<T> mutator) {
		if (completed) {
			return;
		}

		mutator.accept(instance);

		if (timeoutFuture != null) {
			timeoutFuture.cancel(false);
		}

		timeoutFuture = scheduler.schedule(this::complete, timeoutNanos, TimeUnit.NANOSECONDS);
	}

	private void complete() {
		T value;

		synchronized (this) {
			if (completed) {
				return;
			}
			completed = true;
			value = instance;
		}

		completer.accept(value);
	}
}
