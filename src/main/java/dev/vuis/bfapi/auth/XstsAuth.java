package dev.vuis.bfapi.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
public class XstsAuth {
	private static final URI AUTHORIZE_URI = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Object refreshLock = new Object();

	private final XblAuth xblAuth;

	private volatile String token = null;
	private volatile String userHash = null;
	private volatile Instant expires = null;

	public void authenticate() throws IOException, InterruptedException {
		log.info("authenticating XSTS");

		String xblToken = xblAuth.tokenOrRefresh();

		JsonObject bodyJson = Util.apply(new JsonObject(), root -> {
			root.add("Properties", Util.apply(new JsonObject(), properties -> {
				properties.addProperty("SandboxId", "RETAIL");
				properties.add("UserTokens", Util.apply(new JsonArray(), userTokens -> {
					userTokens.add(xblToken);
				}));
			}));
			root.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
			root.addProperty("TokenType", "JWT");
		});
		String body = Util.COMPACT_GSON.toJson(bodyJson);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(AUTHORIZE_URI)
			.header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/json")
			.header(HttpHeaderNames.ACCEPT.toString(), "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			throw new RuntimeException("xsts authentication failed (" + response.statusCode() + "):\n" + response.body());
		}

		JsonObject json = Util.COMPACT_GSON.fromJson(response.body(), JsonObject.class);
		token = json.get("Token").getAsString();
		userHash = json.get("DisplayClaims").getAsJsonObject()
			.get("xui").getAsJsonArray()
			.get(0).getAsJsonObject()
			.get("uhs").getAsString();
		expires = Instant.parse(json.get("NotAfter").getAsString()).minusSeconds(10);

		log.info("authenticated XSTS successfully");
	}

	public String tokenOrRefresh() throws IOException, InterruptedException {
		if (token == null || Instant.now().isAfter(expires)) {
			synchronized (refreshLock) {
				log.info("refreshing expired xsts token");
				authenticate();
			}
		}
		return token;
	}

	public String userHashOrRefresh() throws IOException, InterruptedException {
		if (userHash == null || Instant.now().isAfter(expires)) {
			synchronized (refreshLock) {
				log.info("Refreshing expired xsts user hash");
				authenticate();
			}
		}
		return userHash;
	}
}
