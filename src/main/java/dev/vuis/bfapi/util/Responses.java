package dev.vuis.bfapi.util;

import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.data.ByteBufWriter;
import dev.vuis.bfapi.http.BfApiError;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class Responses {
	private Responses() {
	}

	public static FullHttpResponse json(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus status, ThrowingConsumer<JsonWriter> writerConsumer) {
		ByteBuf buf = ctx.alloc().buffer();
		JsonWriter writer = new JsonWriter(new ByteBufWriter(buf, StandardCharsets.UTF_8));

		try {
			writerConsumer.accept(writer);
		} catch (Exception e) {
			buf.release();
			log.error("failed to serialize json", e);
			return BfApiError.SERIALIZATION_ERROR.response(ctx, msg);
		}

		FullHttpResponse response = new DefaultFullHttpResponse(msg.protocolVersion(), status, buf);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");

		return response;
	}

	public static @Nullable FullHttpResponse checkMethod(ChannelHandlerContext ctx, FullHttpRequest msg, HttpMethod... allowed) {
		if (Arrays.stream(allowed).noneMatch(method -> method.equals(msg.method()))) {
			FullHttpResponse response = BfApiError.INVALID_METHOD.response(ctx, msg);
			response.headers().add(
				HttpHeaderNames.ALLOW,
				Arrays.stream(allowed).map(HttpMethod::name).collect(Collectors.joining(", "))
			);
			return response;
		}
		return null;
	}

	public static String formatInstant(Instant instant) {
		return DateTimeFormatter.RFC_1123_DATE_TIME.format(
			instant.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneOffset.UTC)
		);
	}

	public static void cacheHeaders(FullHttpResponse response, Instant expires) {
		response.headers()
			.set(HttpHeaderNames.EXPIRES, formatInstant(expires))
			.set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=" + Math.max(Duration.between(Instant.now(), expires).getSeconds(), 0));
	}

	public static @Nullable FullHttpResponse checkIfModifiedSince(FullHttpRequest msg, Instant lastModified) {
		if (msg.headers().contains(HttpHeaderNames.IF_MODIFIED_SINCE)) {
			Instant requestModifiedSince;
			try {
				requestModifiedSince = DateTimeFormatter.RFC_1123_DATE_TIME
					.parse(msg.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE), ZonedDateTime::from)
					.toInstant();
			} catch (DateTimeParseException e) {
				return null;
			}

			if (!lastModified.truncatedTo(ChronoUnit.SECONDS).isAfter(requestModifiedSince)) {
				FullHttpResponse response = new DefaultFullHttpResponse(
					msg.protocolVersion(),
					HttpResponseStatus.NOT_MODIFIED
				);
				response.headers().set(
					HttpHeaderNames.LAST_MODIFIED,
					Responses.formatInstant(lastModified)
				);
				return response;
			}
		}

		return null;
	}

	public static void lastModifiedHeaders(FullHttpResponse response, Instant lastModified) {
		response.headers().set(HttpHeaderNames.LAST_MODIFIED, formatInstant(lastModified));
	}
}
