package dev.vuis.bfapi.cloud.cache;

import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import dev.vuis.bfapi.cloud.BfConnection;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SingletonCacheHolder<T> {
	private final BfConnection connection;
	private final RequestType requestType;
	private final Duration lifetime;

	private final AtomicReference<T> currentValue = new AtomicReference<>();
	private final AtomicReference<Instant> lastUpdated = new AtomicReference<>();
	private final AtomicReference<CompletableFuture<T>> pending = new AtomicReference<>();

	public CompletableFuture<T> get() {
		T value = currentValue.get();
		Instant lastUpdatedNow = lastUpdated.get();
		if (value != null && lastUpdatedNow != null && Duration.between(lastUpdatedNow, Instant.now()).compareTo(lifetime) < 0) {
			return CompletableFuture.completedFuture(value);
		}

		CompletableFuture<T> pendingNow = pending.get();
		if (pendingNow != null) {
			return pendingNow;
		}

		CompletableFuture<T> future = new CompletableFuture<>();
		if (pending.compareAndSet(null, future)) {
			request();

			return future;
		} else {
			return pending.get();
		}
	}

	private void request() {
		connection.sendPacket(new PacketClientRequest(
			EnumSet.of(requestType),
			ObjectList.of()
		));
	}

	public void complete(T data) {
		CompletableFuture<T> pendingNow = pending.get();
		if (pendingNow != null) {
			pendingNow.complete(data);
			pending.set(null);
		}
		currentValue.set(data);
		lastUpdated.set(Instant.now());
	}

	public void complete(Exception e) {
		CompletableFuture<T> pendingNow = pending.get();
		if (pendingNow != null) {
			pendingNow.completeExceptionally(e);
			pending.set(null);
		}
	}
}
