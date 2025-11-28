package dev.vuis.bfapi.util.cache;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

public record ExpiryHolder<T>(@NotNull T value, @NotNull Instant expires) {
}
