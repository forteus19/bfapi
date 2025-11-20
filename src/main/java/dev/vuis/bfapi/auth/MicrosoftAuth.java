package dev.vuis.bfapi.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.util.Util;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MicrosoftAuth {
    public static final String XBOX_LIVE_SCOPE = "XboxLive.signin offline_access";

    private static final Random SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE_64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Object refreshLock = new Object();

    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String redirectUri;

    private volatile String accessToken = null;
    private volatile String refreshToken = null;
    private volatile Instant expires = null;

    public String getAuthUri(@NonNull String scope, @NonNull String state) {
        return "https://login.microsoftonline.com/" + Util.urlEncode(tenantId) + "/oauth2/v2.0/authorize" +
            "?client_id=" + Util.urlEncode(clientId) +
            "&response_type=code" +
            "&redirect_uri=" + Util.urlEncode(redirectUri) +
            "&response_mode=query" +
            "&scope=" + Util.urlEncode(scope) +
            "&state=" + Util.urlEncode(state);
    }

    public void redeemCode(@NonNull String code, boolean isRefresh) throws IOException, InterruptedException {
        log.info("Redeeming microsoft authentication code");

        String uri = "https://login.microsoftonline.com/" + Util.urlEncode(tenantId) + "/oauth2/v2.0/token";
        String body = "client_id=" + Util.urlEncode(clientId) +
            "&client_secret=" + Util.urlEncode(clientSecret) +
            "&grant_type=" + (isRefresh ? "refresh_token" : "authorization_code") +
            (isRefresh ? "&refresh_token=" : "&code=") + Util.urlEncode(code) +
            (isRefresh ? "" : "&redirect_uri=" + Util.urlEncode(redirectUri));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (!Util.isSuccess(response.statusCode())) {
            throw new RuntimeException("code redeem failed:\n" + response.body());
        }

        JsonObject json = Util.GSON.fromJson(response.body(), JsonObject.class);
        accessToken = json.get("access_token").getAsString();
        refreshToken = json.get("refresh_token").getAsString();
        expires = Instant.now().plusSeconds(Math.max(json.get("expires_in").getAsLong() - 10, 0));
    }

    public String getOrRefresh() throws IOException, InterruptedException {
        if (refreshToken == null) {
            throw new IllegalStateException("not authorized yet");
        }
        if (Instant.now().isAfter(expires)) {
            synchronized (refreshLock) {
                log.info("Refreshing expired microsoft access token");
                redeemCode(refreshToken, true);
            }
        }
        return accessToken;
    }

    public static String randomState() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE_64_ENCODER.encodeToString(bytes);
    }
}
