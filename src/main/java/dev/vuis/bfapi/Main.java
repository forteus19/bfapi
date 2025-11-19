package dev.vuis.bfapi;

import dev.vuis.bfapi.auth.MicrosoftAuth;
import dev.vuis.bfapi.util.Util;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String msClientId = Util.getEnvOrThrow("MS_CLIENT_ID");

        MicrosoftAuth msAuth = new MicrosoftAuth(msClientId, "consumers");

        String state = MicrosoftAuth.randomState();
    }
}
