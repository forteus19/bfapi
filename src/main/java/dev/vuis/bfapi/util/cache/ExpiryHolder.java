package dev.vuis.bfapi.util.cache;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ExpiryHolder<T>(@NotNull T value, @Nullable Instant expires) {
}
