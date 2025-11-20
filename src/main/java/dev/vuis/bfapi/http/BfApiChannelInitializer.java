package dev.vuis.bfapi.http;

import dev.vuis.bfapi.auth.MsCodeWrapper;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class BfApiChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final MsCodeWrapper msCodeWrapper;

	@Override
	protected void initChannel(SocketChannel ch) {
		ch.pipeline()
			.addLast("codec", new HttpServerCodec())
			.addLast("aggregator", new HttpObjectAggregator(65535))
			.addLast("handler", new BfApiInboundHandler(msCodeWrapper));
	}
}
