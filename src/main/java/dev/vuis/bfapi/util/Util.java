package dev.vuis.bfapi.util;

import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Util {
	public static final String UUID_PATTERN = "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}";
	public static final Gson COMPACT_GSON = new GsonBuilder()
		.setFormattingStyle(FormattingStyle.COMPACT)
		.create();
	public static final Gson PRETTY_GSON = new GsonBuilder()
		.setFormattingStyle(FormattingStyle.PRETTY)
		.create();

    private Util() {
    }

	public static Gson gson(boolean pretty) {
		return pretty ? PRETTY_GSON : COMPACT_GSON;
	}

	public static <T> T apply(T obj, Consumer<T> applier) {
		applier.accept(obj);
		return obj;
	}

	public static <T, R> R ifNonNull(@Nullable T obj, Function<T, R> applier) {
		if (obj != null) {
			return applier.apply(obj);
		} else {
			return null;
		}
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
        return uuid.toString().replace("-", "");
    }

	public static byte[] parseHexArray(@NotNull String str) {
		if (str.length() % 2 != 0) {
			throw new IllegalArgumentException("invalid hex string");
		}

		int resultLen = str.length() / 2;
		byte[] result = new byte[resultLen];
		for (int i = 0; i < resultLen; i++) {
			int s = i * 2;
			result[i] = (byte) Integer.parseUnsignedInt(str.substring(s, s + 2), 16);
		}

		return result;
	}
}
