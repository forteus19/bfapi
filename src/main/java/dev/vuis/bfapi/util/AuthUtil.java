package dev.vuis.bfapi.util;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

@Slf4j
public final class AuthUtil {
	private static final URI MC_JOIN_SERVER_URI = URI.create("https://sessionserver.mojang.com/session/minecraft/join");

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	private AuthUtil() {
	}

	public static JavaAuthManager tryLoadAuthJson(net.lenni0451.commons.httpclient.HttpClient authHttpClient, Path tokensJsonPath) {
		if (!Files.isRegularFile(tokensJsonPath)) {
			return null;
		}

		try (BufferedReader tokensReader = Files.newBufferedReader(tokensJsonPath)) {
			return JavaAuthManager.fromJson(authHttpClient, Util.PRETTY_GSON.fromJson(tokensReader, JsonObject.class));
		} catch (Exception e) {
			return null;
		}
	}

	public static void saveAuthJson(JavaAuthManager authManager, Path tokensJsonPath) throws IOException {
		JsonObject serializedTokens = JavaAuthManager.toJson(authManager);
		try (BufferedWriter tokensWriter = Files.newBufferedWriter(tokensJsonPath)) {
			Util.PRETTY_GSON.toJson(serializedTokens, tokensWriter);
		}
	}

	public static JavaAuthManager createAuthManagerFromLogin(net.lenni0451.commons.httpclient.HttpClient authHttpClient) throws IOException, InterruptedException, TimeoutException {
		return JavaAuthManager.create(authHttpClient).login(
			DeviceCodeMsaAuthService::new,
			(Consumer<MsaDeviceCode>) code -> log.info("microsoft auth URL: {}", code.getDirectVerificationUri())
		);
	}

	public static void mcJoinServer(UUID profileUuid, String accessToken, String serverId, String userAgent) throws IOException, InterruptedException {
		log.info("joining session server");

		JsonObject bodyJson = Util.apply(new JsonObject(), root -> {
			root.addProperty("accessToken", accessToken);
			root.addProperty("selectedProfile", Util.getUndashedUuid(profileUuid));
			root.addProperty("serverId", serverId);
		});
		String body = Util.COMPACT_GSON.toJson(bodyJson);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(MC_JOIN_SERVER_URI)
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.header("Content-Type", "application/json; charset=utf-8")
			.header("User-Agent", userAgent)
			.build();

		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			throw new RuntimeException("join server request failed (" + response.statusCode() + "):\n" + response.body());
		}
	}
}
