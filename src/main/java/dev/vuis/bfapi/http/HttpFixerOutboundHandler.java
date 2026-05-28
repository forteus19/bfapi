package dev.vuis.bfapi.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;

public class HttpFixerOutboundHandler extends ChannelOutboundHandlerAdapter {
	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (!(msg instanceof FullHttpResponse httpResponse)) {
			super.write(ctx, msg, promise);
			return;
		}

		HttpHeaders headers = httpResponse.headers();

		if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
			headers.addInt(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
		}
		if (!headers.contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)) {
			headers.add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		}

		super.write(ctx, msg, promise);
	}
}
