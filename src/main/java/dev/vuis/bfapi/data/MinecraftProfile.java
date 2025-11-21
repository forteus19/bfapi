package dev.vuis.bfapi.data;

import java.util.UUID;

public record MinecraftProfile(
    UUID uuid,
    String username
) {
}
