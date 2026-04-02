package dev.vuis.bfapi.data;

import dev.vuis.bfapi.util.Util;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.vuis.bfapi.util.EnvUtil.getOrDefault;
import static dev.vuis.bfapi.util.EnvUtil.getOrNull;
import static dev.vuis.bfapi.util.EnvUtil.getOrThrow;

public final class BfApiConfig {
	private static final int DEFAULT_BF_CLOUD_PORT = 1924;

	private static BfApiConfig instance = null;

	@Getter
	private final int apiPort;
	@Getter
	private final @NotNull String httpUserAgent;
	@Getter
	private final @NotNull Path tokensJsonPath;
	@Getter
	private final @NotNull InetSocketAddress bfCloudAddress;
	@Getter
	private final @NotNull String bfVersion;
	@Getter
	private final @NotNull String bfVersionHash;
	@Getter
	private final byte @NotNull [] bfHardwareId;
	@Getter
	private final @Nullable Path bfPlayerListPath;
	@Getter
	private final @Nullable String bfUcdRefreshSecret;
	@Getter
	private final boolean bfUcdWriteFilteredPlayers;
	@Getter
	private final boolean bfScrapeFriends;
	@Getter
	private final int bfScrapeFriendsDepth;

	private BfApiConfig() {
		apiPort = getOrDefault("API_PORT", 19190);
		httpUserAgent = getOrDefault("HTTP_USER_AGENT", "bfapi/1.0-SNAPSHOT");
		tokensJsonPath = getOrDefault("TOKENS_JSON_PATH", Path::of, () -> Path.of("bfapi_auth_tokens.json"));
		bfCloudAddress = getOrDefault(
			"BF_CLOUD_ADDRESS",
			s -> Util.parseInetSocketAddress(s, DEFAULT_BF_CLOUD_PORT),
			() -> new InetSocketAddress("cloud.blockfrontmc.com", DEFAULT_BF_CLOUD_PORT)
		);
		bfVersion = getOrThrow("BF_VERSION");
		bfVersionHash = getOrThrow("BF_VERSION_HASH");
		bfHardwareId = getOrThrow("BF_HARDWARE_ID", Util::parseHexArray);
		bfPlayerListPath = getOrNull("BF_PLAYER_LIST_PATH", Path::of);
		bfUcdRefreshSecret = getOrNull("BF_UCD_REFRESH_SECRET");
		bfUcdWriteFilteredPlayers = getOrDefault("BF_UCD_WRITE_FILTERED_PLAYERS", false);
		bfScrapeFriends = getOrDefault("BF_SCRAPE_FRIENDS", false);
		bfScrapeFriendsDepth = getOrDefault("BF_SCRAPE_FRIENDS_DEPTH", 2);
	}

	public static BfApiConfig instance() {
		if (instance == null) {
			instance = new BfApiConfig();
		}
		return instance;
	}
}
