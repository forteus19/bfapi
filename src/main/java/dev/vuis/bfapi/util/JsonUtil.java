package dev.vuis.bfapi.util;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonUtil {
	private JsonUtil() {
	}

	public static @NotNull JsonWriter nullableValue(@NotNull JsonWriter w, @Nullable String value) throws IOException {
		if (value == null) {
			w.nullValue();
		} else {
			w.value(value);
		}
		return w;
	}
}
