package dev.vuis.bfapi.util;

import com.google.gson.Gson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public final class Util {
	public static final Gson GSON = new Gson();

    private Util() {
    }

	public static <T> T apply(T obj, Consumer<T> applier) {
		applier.accept(obj);
		return obj;
	}

    public static String getEnvOrThrow(@NotNull String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new RuntimeException("Environment variable " + name + " is not set");
        }
        return value;
    }

    public static String urlEncode(@NotNull String str) {
		return URLEncoder.encode(str, StandardCharsets.UTF_8);
	}

	public static boolean isSuccess(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}

    public static UUID parseUndashedUuid(@NotNull String str) {
        if (str.length() != 32) {
            throw new IllegalArgumentException("invalid undashed uuid");
        }
        return new UUID(
            Long.parseUnsignedLong(str.substring(0, 16), 16),
            Long.parseUnsignedLong(str.substring(16, 32), 16)
        );
    }

    public static String getUndashedUuid(@NotNull UUID uuid) {
        return "%016x%016x".formatted(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }
}
