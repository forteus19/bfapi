package dev.vuis.bfapi.auth;

import com.google.gson.JsonObject;
import dev.vuis.bfapi.data.MinecraftProfile;
import dev.vuis.bfapi.util.Util;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MinecraftAuth {
	private static final URI AUTHENTICATE_URI = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
	private static final URI PROFILE_URI = URI.create("https://api.minecraftservices.com/minecraft/profile");
	private static final URI JOIN_SERVER_URI = URI.create("https://sessionserver.mojang.com/session/minecraft/join");

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Object refreshLock = new Object();

	private final XstsAuth xstsAuth;

	private volatile String token = null;
	private volatile Instant expires = null;

	public void authenticate() throws IOException, InterruptedException {
		log.info("authenticating minecraft");

		String xstsToken = xstsAuth.tokenOrRefresh();
		String xstsUserHash = xstsAuth.userHashOrRefresh();

		JsonObject bodyJson = Util.apply(new JsonObject(), root -> {
			root.addProperty("identityToken", "XBL3.0 x=" + xstsUserHash + ";" + xstsToken);
		});
		String body = Util.COMPACT_GSON.toJson(bodyJson);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(AUTHENTICATE_URI)
			.header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/json")
			.header(HttpHeaderNames.ACCEPT.toString(), "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			throw new RuntimeException("xsts authentication failed (" + response.statusCode() + "):\n" + response.body());
		}

		JsonObject json = Util.COMPACT_GSON.fromJson(response.body(), JsonObject.class);
		token = json.get("access_token").getAsString();
		expires = Instant.now().plusSeconds(Math.max(json.get("expires_in").getAsLong() - 10, 0));

		log.info("authenticated minecraft successfully");
	}

	public String tokenOrRefresh() throws IOException, InterruptedException {
		if (token == null || Instant.now().isAfter(expires)) {
			synchronized (refreshLock) {
				log.info("refreshing expired minecraft token");
				authenticate();
			}
		}
		return token;
	}

	public MinecraftProfile retrieveProfile() throws IOException, InterruptedException {
		log.info("retrieving minecraft profile");

		String refreshedToken = tokenOrRefresh();

		HttpRequest request = HttpRequest.newBuilder()
			.uri(PROFILE_URI)
			.header(HttpHeaderNames.AUTHORIZATION.toString(), "Bearer " + refreshedToken)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			throw new RuntimeException("xsts authentication failed (" + response.statusCode() + "):\n" + response.body());
		}

		JsonObject json = Util.COMPACT_GSON.fromJson(response.body(), JsonObject.class);
		return new MinecraftProfile(
			Util.parseUndashedUuid(json.get("id").getAsString()),
			json.get("name").getAsString()
		);
	}

	public void joinServer(MinecraftProfile profile, String serverId) throws IOException, InterruptedException {
		log.info("joining session server");

		String refreshedToken = tokenOrRefresh();

		JsonObject bodyJson = Util.apply(new JsonObject(), root -> {
			root.addProperty("accessToken", refreshedToken);
			root.addProperty("selectedProfile", Util.getUndashedUuid(profile.uuid()));
			root.addProperty("serverId", serverId);
		});
		String body = Util.COMPACT_GSON.toJson(bodyJson);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(JOIN_SERVER_URI)
			.header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			throw new RuntimeException("xsts authentication failed (" + response.statusCode() + "):\n" + response.body());
		}
	}
}
