package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.packet.PacketCodec;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BfCloudChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final BfConnection connection;

	@Override
	protected void initChannel(SocketChannel ch) {
		ChannelPipeline pipeline = ch.pipeline();

		pipeline.addLast("idleTimeout", new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
		pipeline.addLast("flushConsolidation", new FlushConsolidationHandler(256, true));

		PacketCodec.installFrameCodec(pipeline);
		PacketCodec.installPacketCodec(pipeline);

		pipeline.addLast("handler", new BfCloudInboundHandler(connection));
	}
}
