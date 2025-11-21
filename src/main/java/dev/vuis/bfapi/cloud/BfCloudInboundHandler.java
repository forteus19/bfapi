package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.IOException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BfCloudInboundHandler extends SimpleChannelInboundHandler<IPacket> {
    private final BfConnection connection;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IPacket packet) throws IOException {
        connection.bumpIdle();
        if (connection.shouldHandlePacket(packet)) {
            PacketRegistry.processPacket(packet, connection.getType(), connection);
        }
    }
}
