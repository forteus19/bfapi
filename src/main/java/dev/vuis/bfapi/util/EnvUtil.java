package dev.vuis.bfapi.util;

import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnvUtil {
	private EnvUtil() {
	}

	public static @Nullable String getOrNull(@NotNull String name) {
		return System.getenv(name);
	}

	public static <T> @Nullable T getOrNull(@NotNull String name, @NotNull Function<String, ? extends T> parser) {
		String value = getOrNull(name);
		if (value == null) {
			return null;
		}
		try {
			return parser.apply(value);
		} catch (Exception e) {
			return null;
		}
	}

	public static @NotNull String getOrThrow(@NotNull String name) {
		String value = System.getenv(name);
		if (value == null) {
			throw new RuntimeException("Environment variable " + name + " is not set");
		}
		return value;
	}

	public static <T> @NotNull T getOrThrow(@NotNull String name, @NotNull Function<String, ? extends T> parser) {
		String value = getOrThrow(name);
		try {
			return parser.apply(value);
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse environment variable " + name, e);
		}
	}

	public static @NotNull String getOrDefault(@NotNull String name, @NotNull String defaultValue) {
		String value = System.getenv(name);
		return value != null ? value : defaultValue;
	}

	public static <T> @NotNull T getOrDefault(@NotNull String name, @NotNull Function<String, ? extends T> parser, @NotNull Supplier<? extends T> defaultValue) {
		String value = System.getenv(name);
		if (value == null) {
			return defaultValue.get();
		}
		try {
			return parser.apply(value);
		} catch (Exception e) {
			return defaultValue.get();
		}
	}

	public static <T> @NotNull T getOrDefault(@NotNull String name, @NotNull Function<String, ? extends T> parser, @NotNull T defaultValue) {
		return getOrDefault(name, parser, () -> defaultValue);
	}

	public static int getOrDefault(@NotNull String name, int defaultValue) {
		return getOrDefault(name, Integer::parseInt, defaultValue);
	}

	public static boolean getOrDefault(@NotNull String name, boolean defaultValue) {
		return getOrDefault(name, Boolean::parseBoolean, defaultValue);
	}
}
