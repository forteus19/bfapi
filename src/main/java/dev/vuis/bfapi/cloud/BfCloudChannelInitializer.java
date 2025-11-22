package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.packet.PacketCodec;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BfCloudChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final BfConnection connection;

	@Override
	protected void initChannel(SocketChannel ch) {
		ch.pipeline()
			.addLast("idleStateHandler", new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
			.addLast("frameDecoder", PacketCodec.createFrameDecoder())
			.addLast("frameEncoder", PacketCodec.createFrameEncoder())
			.addLast("packetDecoder", new PacketCodec.PacketDecoder())
			.addLast("packetEncoder", new PacketCodec.PacketEncoder())
			.addLast("handler", new BfCloudInboundHandler(connection));
	}
}
