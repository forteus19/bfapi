package dev.vuis.bfapi.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;

public final class Util {
    private Util() {
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
}
