package dev.vuis.bfapi.util;

import com.google.common.base.Suppliers;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AuthUtil {
	private static final URI MC_JOIN_SERVER_URI = URI.create("https://sessionserver.mojang.com/session/minecraft/join");

	private static final Supplier<HttpClient> HTTP_CLIENT = Suppliers.memoize(HttpClient::newHttpClient);

	private AuthUtil() {
	}

	public static void mcJoinServer(UUID profileUuid, String accessToken, String serverId) throws IOException, InterruptedException {
		log.info("joining session server");

		JsonObject bodyJson = Util.apply(new JsonObject(), root -> {
			root.addProperty("accessToken", accessToken);
			root.addProperty("selectedProfile", Util.getUndashedUuid(profileUuid));
			root.addProperty("serverId", serverId);
		});
		String body = Util.COMPACT_GSON.toJson(bodyJson);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(MC_JOIN_SERVER_URI)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		HttpResponse<String> response = HTTP_CLIENT.get().send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			throw new RuntimeException("join server request failed (" + response.statusCode() + "):\n" + response.body());
		}
	}
}
