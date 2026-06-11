package dev.vuis.bfapi.cache;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SingletonCacheHolder<T> {
	private final Runnable requester;

	private final AtomicReference<T> valueReference = new AtomicReference<>();
	private final AtomicReference<CompletableFuture<T>> pendingReference = new AtomicReference<>();
	private final AtomicReference<Instant> lastUpdatedReference = new AtomicReference<>();

	public CompletableFuture<T> get() {
		T value = valueReference.get();
		if (value != null) {
			return CompletableFuture.completedFuture(value);
		}

		CompletableFuture<T> pending = pendingReference.get();
		if (pending != null) {
			return pending;
		}

		CompletableFuture<T> future = new CompletableFuture<>();
		if (pendingReference.compareAndSet(null, future)) {
			try {
				request();
			} catch (Exception e) {
				pendingReference.set(null);
				future.completeExceptionally(e);
			}

			return future;
		} else {
			return pendingReference.get();
		}
	}

	public void request() {
		requester.run();
	}

	public void complete(T data) {
		CompletableFuture<T> pending = pendingReference.get();
		if (pending != null) {
			pendingReference.set(null);
			pending.complete(data);
		}

		valueReference.set(data);
		lastUpdatedReference.set(Instant.now());
	}

	public void complete(Exception e) {
		CompletableFuture<T> pending = pendingReference.get();
		if (pending != null) {
			pendingReference.set(null);
			pending.completeExceptionally(e);
		}
	}

	public Instant getLastUpdated() {
		return lastUpdatedReference.get();
	}

	public synchronized void purge() {
		valueReference.set(null);
		CompletableFuture<T> pending = pendingReference.get();
		if (pending != null) {
			pendingReference.set(null);
			pending.completeExceptionally(new PurgeException());
		}
		lastUpdatedReference.set(null);
	}
}
