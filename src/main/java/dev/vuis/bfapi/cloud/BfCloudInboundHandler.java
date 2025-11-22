package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.PacketRegistry;
import com.boehmod.bflib.cloud.packet.primitives.EncryptionKeyExchangePacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class BfCloudInboundHandler extends SimpleChannelInboundHandler<IPacket> {
	private final BfConnection connection;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, IPacket packet) throws IOException {
		log.info("received packet {}", packet.getClass().getSimpleName());

		connection.bumpIdle();

		if (packet instanceof EncryptionKeyExchangePacket keyExchangePacket) {
			try {
				connection.handleKeyExchange(keyExchangePacket);
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}
		} else if (connection.shouldHandlePacket(packet)) {
			PacketRegistry.processPacket(packet, connection.getType(), connection);
		}
	}
}
