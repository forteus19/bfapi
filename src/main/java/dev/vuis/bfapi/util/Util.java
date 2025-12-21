package dev.vuis.bfapi.util;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.PlayerRank;
import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.util.cache.ExpiryHolder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Util {
	private static final Base64.Encoder BASE64_ENCODER_NO_PADDING = Base64.getEncoder().withoutPadding();
	public static final int PRESTIGE_EXP = PlayerRank.getTotalRequiredEXPForRank(PlayerRank.GENERAL);

	public static final Gson COMPACT_GSON = baseGsonBuilder()
		.setFormattingStyle(FormattingStyle.COMPACT)
		.create();
	public static final Gson PRETTY_GSON = baseGsonBuilder()
		.setFormattingStyle(FormattingStyle.PRETTY)
		.create();

	private Util() {
	}

	private static GsonBuilder baseGsonBuilder() {
		return new GsonBuilder()
			.serializeNulls();
	}

	public static Gson gson(boolean pretty) {
		return pretty ? PRETTY_GSON : COMPACT_GSON;
	}

	public static <T> Consumer<T> unchecked(ThrowingConsumer<T> consumer) {
		return t -> {
			try {
				consumer.accept(t);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
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

	public static String getEnvOrElse(@NotNull String name, @NotNull String defaultValue) {
		String value = System.getenv(name);
		return value != null ? value : defaultValue;
	}

	public static String urlEncode(@NotNull String str) {
		return URLEncoder.encode(str, StandardCharsets.UTF_8);
	}

	public static boolean isSuccess(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}

	public static Optional<UUID> parseUuidLenient(@NotNull String str) {
		try {
			return Optional.of(UUID.fromString(str));
		} catch (Exception e1) {
			try {
				return Optional.of(parseUndashedUuid(str));
			} catch (Exception e2) {
				return Optional.empty();
			}
		}
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

	public static byte[] getUuidBytes(@NotNull UUID uuid) {
		ByteBuffer buf = ByteBuffer.allocate(16);
		buf.putLong(uuid.getMostSignificantBits());
		buf.putLong(uuid.getLeastSignificantBits());
		return buf.array();
	}

	public static String getBase64Uuid(@NotNull UUID uuid) {
		return BASE64_ENCODER_NO_PADDING.encodeToString(getUuidBytes(uuid));
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

	public static String createHexString(byte @NotNull [] bytes) {
		StringBuilder sb = new StringBuilder();

		for (byte b : bytes) {
			String byteStr = Integer.toHexString(255 & b);
			if (byteStr.length() == 1) {
				sb.append('0');
			}
			sb.append(byteStr);
		}

		return sb.toString();
	}

	public static @NotNull String getCachedPlayerName(@Nullable BfDataCache dataCache, @NotNull UUID uuid) {
		String name = "Unknown";

		if (dataCache != null) {
			BfPlayerData playerData = Util.ifNonNull(dataCache.playerData.getIfPresent(uuid), ExpiryHolder::value);
			if (playerData != null) {
				name = playerData.getUsername();
			}
		}

		return name;
	}

	public static @NotNull String getCachedClanName(@Nullable BfDataCache dataCache, @NotNull UUID uuid) {
		String name = "Unknown";

		if (dataCache != null) {
			AbstractClanData playerData = Util.ifNonNull(dataCache.clanData.getIfPresent(uuid), ExpiryHolder::value);
			if (playerData != null) {
				name = playerData.getName();
			}
		}

		return name;
	}

	public static int getTotalExp(int prestige, int exp) {
		return prestige * PRESTIGE_EXP + exp;
	}

	public static <T> int indexOf(List<T> list, Predicate<T> filter) {
		for (int i = 0; i < list.size(); i++) {
			if (filter.test(list.get(i))) {
				return i;
			}
		}
		return -1;
	}
}
