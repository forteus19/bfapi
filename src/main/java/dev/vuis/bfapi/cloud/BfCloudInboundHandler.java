package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.connection.AbstractConnectionInboundHandler;
import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.PacketRegistry;
import com.boehmod.bflib.cloud.packet.primitives.EncryptionKeyExchangePacket;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
public class BfCloudInboundHandler extends AbstractConnectionInboundHandler<BfConnection> {
	public BfCloudInboundHandler(@NonNull BfConnection connectionHandler) {
		super(connectionHandler, "connection closed by cloud");
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object object) throws IOException {
		if (!(object instanceof IPacket packet)) {
			return;
		}

		log.trace("received packet {}", packet);

		if (packet instanceof EncryptionKeyExchangePacket keyExchangePacket) {
			try {
				connectionHandler.handleKeyExchange(keyExchangePacket);
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}
		} else if (connectionHandler.shouldHandlePacket(packet)) {
			PacketRegistry.processPacket(packet, connectionHandler.getType(), connectionHandler);
		}
	}

	@Override
	protected void onIdleTimeout() {
		connectionHandler.disconnect("idle timeout", false);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.error("channel exception", cause);
		connectionHandler.disconnect("channel exception", false);
	}
}
