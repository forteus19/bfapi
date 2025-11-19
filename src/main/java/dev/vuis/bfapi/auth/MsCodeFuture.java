package dev.vuis.bfapi.auth;

import java.util.concurrent.CompletableFuture;

public record MsCodeFuture(
	CompletableFuture<String> future,
	String state
) {
}
