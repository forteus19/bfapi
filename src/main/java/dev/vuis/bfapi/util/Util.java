package dev.vuis.bfapi.util;

import com.google.gson.Gson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import lombok.NonNull;

public final class Util {
	public static final Gson GSON = new Gson();

    private Util() {
    }

	public static <T> T apply(T obj, Consumer<T> applier) {
		applier.accept(obj);
		return obj;
	}

    public static String getEnvOrThrow(@NonNull String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new RuntimeException("Environment variable " + name + " is not set");
        }
        return value;
    }

    public static String urlEncode(@NonNull String str) {
		return URLEncoder.encode(str, StandardCharsets.UTF_8);
	}

	public static boolean isSuccess(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}
}
