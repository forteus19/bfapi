package dev.vuis.bfapi.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class BfApiChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final BfApiInboundHandler inboundHandler;

	@Override
	protected void initChannel(SocketChannel ch) {
		ch.pipeline()
			.addLast("codec", new HttpServerCodec())
			.addLast("aggregator", new HttpObjectAggregator(65535))
			.addLast("handler", inboundHandler);
	}
}
