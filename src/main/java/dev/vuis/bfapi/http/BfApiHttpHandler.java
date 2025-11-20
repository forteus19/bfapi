package dev.vuis.bfapi.http;

import dev.vuis.bfapi.auth.MsCodeFuture;
import dev.vuis.bfapi.util.Util;
import io.netty.channel.ChannelFutureListener;
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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class BfApiHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	public static final String AUTH_CALLBACK_PATH = "/server_auth_callback";

	private final MsCodeFuture msCodeFuture;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
		boolean keepAlive = HttpUtil.isKeepAlive(msg);

		QueryStringDecoder qs = new QueryStringDecoder(msg.uri());
		FullHttpResponse response = switch (qs.path()) {
			case AUTH_CALLBACK_PATH -> serverAuthCallback(ctx, msg, qs);
			default -> Util.errorResponse(
				ctx, msg,
				HttpResponseStatus.NOT_FOUND,
				"not_found"
			);
		};

		response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
		if (keepAlive) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(response);
		} else {
			ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		}
	}

	private FullHttpResponse serverAuthCallback(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Util.errorResponse(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}

		if (msCodeFuture.future().isDone()) {
			return Util.errorResponse(
				ctx, msg,
				HttpResponseStatus.FORBIDDEN,
				"already_authenticated"
			);
		}

		if (!qs.parameters().containsKey("code")) {
			return Util.errorResponse(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_code"
			);
		}
		if (!qs.parameters().containsKey("state")) {
			return Util.errorResponse(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_state"
			);
		}

		String state = qs.parameters().get("state").getFirst();
		if (!state.equals(msCodeFuture.state())) {
			return Util.errorResponse(
				ctx, msg,
				HttpResponseStatus.FORBIDDEN,
				"unexpected_state"
			);
		}

		String code = qs.parameters().get("code").getFirst();
		msCodeFuture.future().complete(code);

		return Util.stringResponse(
			ctx, msg,
			HttpResponseStatus.OK,
			"Authentication completed"
		);
	}
}
