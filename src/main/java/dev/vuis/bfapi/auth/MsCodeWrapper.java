package dev.vuis.bfapi.auth;

import java.util.concurrent.CompletableFuture;

public record MsCodeWrapper(
	CompletableFuture<String> future,
	String state
) {
}
