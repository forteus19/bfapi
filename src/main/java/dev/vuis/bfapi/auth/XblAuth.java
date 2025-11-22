package dev.vuis.bfapi.auth;

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
public class XblAuth {
    private static final URI AUTHENTICATE_URI = URI.create("https://user.auth.xboxlive.com/user/authenticate");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Object refreshLock = new Object();

    private final MicrosoftAuth microsoftAuth;

    private volatile String token = null;
    private volatile Instant expires = null;

    public void authenticate() throws IOException, InterruptedException {
        log.info("authenticating xbox live");

        String msToken = microsoftAuth.tokenOrRefresh();

        JsonObject bodyJson = Util.apply(new JsonObject(), root -> {
            root.add("Properties", Util.apply(new JsonObject(), properties -> {
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", msToken);
            }));
            root.addProperty("RelyingParty", "http://auth.xboxlive.com");
            root.addProperty("TokenType", "JWT");
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
            throw new RuntimeException("xbl authentication failed (" + response.statusCode() + "):\n" + response.body());
        }

        JsonObject json = Util.COMPACT_GSON.fromJson(response.body(), JsonObject.class);
        token = json.get("Token").getAsString();
        expires = Instant.parse(json.get("NotAfter").getAsString()).minusSeconds(10);

		log.info("authenticated xbox live successfully");
    }

    public String tokenOrRefresh() throws IOException, InterruptedException {
        if (token == null || Instant.now().isAfter(expires)) {
            synchronized (refreshLock) {
                log.info("refreshing expired xbl token");
                authenticate();
            }
        }
        return token;
    }
}
