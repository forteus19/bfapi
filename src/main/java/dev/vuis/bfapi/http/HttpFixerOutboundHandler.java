package dev.vuis.bfapi.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;

public class HttpFixerOutboundHandler extends ChannelOutboundHandlerAdapter {
	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (!(msg instanceof FullHttpResponse httpResponse)) {
			super.write(ctx, msg, promise);
			return;
		}
		if (!httpResponse.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
			httpResponse.headers().addInt(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
		}
		if (!httpResponse.headers().contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)) {
			httpResponse.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		}
		ctx.write(httpResponse, promise);
	}
}
