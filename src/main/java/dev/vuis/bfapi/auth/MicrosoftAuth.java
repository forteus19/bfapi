package dev.vuis.bfapi.auth;

import dev.vuis.bfapi.util.Util;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MicrosoftAuth {
    public static final String XBOX_LIVE_SCOPE = "XboxLive.signin offline_access";

    private static final Random SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE_64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final String clientId;
    private final String tenantId;

    public String getAuthUri(@NonNull String redirectUri, @NonNull String scope, @NonNull String state) {
        return "https://login.microsoftonline.com/" + Util.urlEncode(tenantId) + "/oauth2/v2.0/authorize"
            + "?client_id=" + Util.urlEncode(clientId)
            + "&response_type=code"
            + "&redirect_uri=" + Util.urlEncode(redirectUri)
            + "&response_mode=query"
            + "&scope=" + Util.urlEncode(scope)
            + "&state=" + Util.urlEncode(state);
    }

    public static String randomState() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE_64_ENCODER.encodeToString(bytes);
    }
}
