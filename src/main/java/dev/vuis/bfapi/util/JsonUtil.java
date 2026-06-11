package dev.vuis.bfapi.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonUtil {
	private JsonUtil() {
	}

	public static @Nullable JsonObject getObject(@NotNull JsonElement element) {
		if (!element.isJsonObject()) {
			return null;
		}
		return element.getAsJsonObject();
	}

	public static @Nullable JsonArray getArray(@NotNull JsonObject object, @NotNull String key) {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		return element.getAsJsonArray();
	}

	public static @Nullable String getString(@NotNull JsonObject object, @NotNull String key) {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isString()) {
			return null;
		}
		return primitive.getAsString();
	}
}
