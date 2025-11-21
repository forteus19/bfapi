package dev.vuis.bfapi.http;

import dev.vuis.bfapi.auth.MsCodeWrapper;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.util.Responses;
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
public final class BfApiInboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	public static final String AUTH_CALLBACK_PATH = "/server_auth_callback";

	private final MsCodeWrapper msCodeWrapper;
	public BfConnection connection = null;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
		boolean keepAlive = HttpUtil.isKeepAlive(msg);

		QueryStringDecoder qs = new QueryStringDecoder(msg.uri());
		FullHttpResponse response = switch (qs.path()) {
			case AUTH_CALLBACK_PATH -> msCodeWrapper != null ? serverAuthCallback(ctx, msg, qs) : null;
			default -> null;
		};

		if (response == null) {
			response = Responses.error(
				ctx, msg,
				HttpResponseStatus.NOT_FOUND,
				"not_found"
			);
		}

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
}
