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
import java.nio.charset.StandardCharsets;

public final class Responses {
    private Responses() {
    }

    public static ByteBuf stringBuf(ByteBufAllocator alloc, String str) {
        byte[] b = str.getBytes(StandardCharsets.UTF_8);
        return alloc.buffer(b.length).writeBytes(b);
    }

    public static ByteBuf jsonBuf(ByteBufAllocator alloc, JsonElement json, boolean pretty) {
        return stringBuf(alloc, Util.gson(pretty).toJson(json));
    }

    public static FullHttpResponse string(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus status, String str) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            msg.protocolVersion(),
            status,
            stringBuf(ctx.alloc(), str)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        return response;
    }

    public static FullHttpResponse json(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus status, JsonElement json, boolean pretty) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            msg.protocolVersion(),
            status,
            jsonBuf(ctx.alloc(), json, pretty)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        return response;
    }

    public static FullHttpResponse error(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus status, String errorId) {
        JsonObject root = new JsonObject();
        root.addProperty("error", errorId);
        return json(ctx, msg, status, root, false);
    }
}
