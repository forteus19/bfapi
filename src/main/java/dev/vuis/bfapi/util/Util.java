package dev.vuis.bfapi.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;

public final class Util {
    private Util() {
    }

    public static String getEnvOrThrow(@NonNull String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new RuntimeException("Environment variable " + name + " is not set");
        }
        return value;
    }

    public static String urlEncode(@NonNull String str) {
		return URLEncoder.encode(str, StandardCharsets.UTF_8);
	}

	public static ByteBuf stringBuf(ByteBufAllocator alloc, String str) {
		byte[] b = str.getBytes(StandardCharsets.UTF_8);
		return alloc.buffer(b.length).writeBytes(b);
	}

	public static FullHttpResponse stringResponse(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus status, String str) {
		FullHttpResponse response = new DefaultFullHttpResponse(
			msg.protocolVersion(),
			status,
			stringBuf(ctx.alloc(), str)
		);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
		return response;
	}

	public static ByteBuf jsonBuf(ByteBufAllocator alloc, JsonElement json) {
		return stringBuf(alloc, json.toString());
	}

	public static FullHttpResponse jsonResponse(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus status, JsonElement json) {
		FullHttpResponse response = new DefaultFullHttpResponse(
			msg.protocolVersion(),
			status,
			jsonBuf(ctx.alloc(), json)
		);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
		return response;
	}

	public static FullHttpResponse errorResponse(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus status, String errorId) {
		JsonObject root = new JsonObject();
		root.addProperty("error", errorId);
		return jsonResponse(ctx, msg, status, root);
	}
}
