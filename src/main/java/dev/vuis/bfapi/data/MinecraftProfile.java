package dev.vuis.bfapi.data;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.util.Util;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public record MinecraftProfile(
	UUID uuid,
	String username
) {
	public static final Cache<String, Optional<MinecraftProfile>> CACHE_BY_NAME = CacheBuilder.newBuilder()
		.expireAfterWrite(Duration.ofMinutes(10))
		.build();

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	public static Optional<MinecraftProfile> retrieveByName(@NotNull String name) throws IOException, InterruptedException {
		String lookupName = name.toLowerCase();

		Optional<MinecraftProfile> cached = CACHE_BY_NAME.getIfPresent(lookupName);
		if (cached != null) {
			return cached;
		}

		log.info("refreshing minecraft profile for {}", lookupName);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + Util.urlEncode(lookupName)))
			.GET()
			.build();

		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			log.error("minecraft profile lookup failed for {} ({}):\n{}", name, response.statusCode(), response.body());

			CACHE_BY_NAME.put(lookupName, Optional.empty());
			return Optional.empty();
		}

		JsonObject json = Util.COMPACT_GSON.fromJson(response.body(), JsonObject.class);
		Optional<MinecraftProfile> profile = Optional.of(new MinecraftProfile(
			Util.parseUndashedUuid(json.get("id").getAsString()),
			json.get("name").getAsString()
		));

		CACHE_BY_NAME.put(lookupName, profile);

		return profile;
	}
}
