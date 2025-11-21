package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.IPacketHandlerFunction;
import com.boehmod.bflib.cloud.packet.PacketRegistry;
import com.boehmod.bflib.cloud.packet.common.server.PacketServerNotification;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BfCloudPacketHandlers {
    private BfCloudPacketHandlers() {
    }

    public static void register() {
        registerPacketHandler(PacketServerNotification.class, BfCloudPacketHandlers::serverNotification);
    }

    private static <P extends IPacket> void registerPacketHandler(Class<P> packetClass, IPacketHandlerFunction<P, BfConnection> packetHandler) {
        PacketRegistry.registerPacketHandler(packetClass, packetHandler, BfConnection.class);
    }

    public static void serverNotification(PacketServerNotification packet, BfConnection connection) {
        log.info("server notification: {}", packet.message());
    }
}
