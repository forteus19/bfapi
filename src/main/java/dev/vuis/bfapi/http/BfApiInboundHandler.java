package dev.vuis.bfapi.http;

import com.google.gson.JsonObject;
import dev.vuis.bfapi.auth.MsCodeWrapper;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.util.Responses;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChannelHandler.Sharable
public final class BfApiInboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	public static final String AUTH_CALLBACK_PATH = "/server_auth_callback";

	private final MsCodeWrapper msCodeWrapper;
	public BfConnection connection = null;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
		QueryStringDecoder qs = new QueryStringDecoder(msg.uri());
		String path = qs.path();
		boolean keepAlive = HttpUtil.isKeepAlive(msg);

		FullHttpResponse response = switch (path) {
			case AUTH_CALLBACK_PATH -> msCodeWrapper != null ? serverAuthCallback(ctx, msg, qs) : null;
			case "/player_data" -> playerData(ctx, msg, qs);
			default -> null;
		};

		if (response == null) {
			response = Responses.error(
				ctx, msg,
				HttpResponseStatus.NOT_FOUND,
				"not_found"
			);
		}

		if (keepAlive) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(response);
		} else {
			ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		}
	}

	private FullHttpResponse serverAuthCallback(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}

		if (msCodeWrapper.future().isDone()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.FORBIDDEN,
				"already_authenticated"
			);
		}

		if (!qs.parameters().containsKey("code")) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_code"
			);
		}
		if (!qs.parameters().containsKey("state")) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_state"
			);
		}

		String state = qs.parameters().get("state").getFirst();
		if (!state.equals(msCodeWrapper.state())) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.FORBIDDEN,
				"unexpected_state"
			);
		}

		String code = qs.parameters().get("code").getFirst();
		msCodeWrapper.future().complete(code);

		return Responses.string(
			ctx, msg,
			HttpResponseStatus.OK,
			"Authentication completed"
		);
	}

	private FullHttpResponse playerData(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}
		if (connection == null || !connection.isConnected()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.SERVICE_UNAVAILABLE,
				"cloud_disconnected"
			);
		}

		if (!qs.parameters().containsKey("uuid")) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_uuid"
			);
		}

		UUID uuid;
		try {
			uuid = UUID.fromString(qs.parameters().get("uuid").getFirst());
		} catch (Exception e) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"invalid_uuid"
			);
		}

		CompletableFuture<JsonObject> data = connection.dataCache.getPlayerData(uuid).thenApply(BfPlayerData::serialize);
        try {
            return Responses.json(
                ctx, msg,
                HttpResponseStatus.OK,
                data.get(10, TimeUnit.SECONDS),
				true
            );
        } catch (ExecutionException | InterruptedException e) {
			log.error("error while retrieving player data", e);
            return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"internal_server_error"
			);
        } catch (TimeoutException e) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.GATEWAY_TIMEOUT,
				"packet_timeout"
			);
        }
    }
}
