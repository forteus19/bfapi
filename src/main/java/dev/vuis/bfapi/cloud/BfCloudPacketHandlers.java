package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.player.PlayerDataContext;
import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.IPacketHandlerFunction;
import com.boehmod.bflib.cloud.packet.PacketRegistry;
import com.boehmod.bflib.cloud.packet.common.PacketChatMessageFromCloud;
import com.boehmod.bflib.cloud.packet.common.PacketNotificationFromCloud;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedClanData;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedCloudData;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedPlayerData;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedPlayerDataSet;
import com.boehmod.bflib.cloud.packet.common.server.PacketServerNotification;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.Pair;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BfCloudPacketHandlers {
	private BfCloudPacketHandlers() {
	}

	public static void register() {
        registerPacketHandler(PacketChatMessageFromCloud.class, BfCloudPacketHandlers::chatMessageFromCloud);
		registerPacketHandler(PacketNotificationFromCloud.class, BfCloudPacketHandlers::notificationFromCloud);
		registerPacketHandler(PacketRequestedClanData.class, BfCloudPacketHandlers::requestedClanData);
		registerPacketHandler(PacketRequestedCloudData.class, BfCloudPacketHandlers::requestedCloudData);
		registerPacketHandler(PacketRequestedPlayerData.class, BfCloudPacketHandlers::requestedPlayerData);
		registerPacketHandler(PacketRequestedPlayerDataSet.class, BfCloudPacketHandlers::requestedPlayerDataSet);
		registerPacketHandler(PacketServerNotification.class, BfCloudPacketHandlers::serverNotification);
	}

	private static <P extends IPacket> void registerPacketHandler(Class<P> packetClass, IPacketHandlerFunction<P, BfConnection> packetHandler) {
		PacketRegistry.registerPacketHandler(packetClass, packetHandler, BfConnection.class);
	}

    private static void chatMessageFromCloud(PacketChatMessageFromCloud packet, BfConnection connection) {
        log.info("cloud chat message: {}", packet.message());
    }

    private static void notificationFromCloud(PacketNotificationFromCloud packet, BfConnection connection) {
		log.info("cloud notification: {}", packet.message());
	}

	private static void requestedClanData(PacketRequestedClanData packet, BfConnection connection) {
		connection.dataCache.clanData.complete(packet.uuid(), packet.clanData());
	}

	private static void requestedCloudData(PacketRequestedCloudData packet, BfConnection connection) {
		connection.dataCache.cloudData.complete(new BfCloudData(
			packet.getUsersOnline(),
			packet.getGamePlayerCount(),
			Instant.ofEpochMilli(packet.getScoreboardResetTime()),
			packet.getPlayerScores(),
			packet.getClanScores()
		));
	}

    private static void requestedPlayerData(PacketRequestedPlayerData packet, BfConnection connection) {
		handlePlayerData(packet.uuid(), packet.context(), packet.data(), connection);
	}

    private static void requestedPlayerDataSet(PacketRequestedPlayerDataSet packet, BfConnection connection) {
		for (Map.Entry<UUID, Pair<PlayerDataContext, byte[]>> entry : packet.dataSet().entrySet()) {
			handlePlayerData(entry.getKey(), entry.getValue().left(), entry.getValue().right(), connection);
		}
	}

    private static void serverNotification(PacketServerNotification packet, BfConnection connection) {
		log.info("server notification: {}", packet.message());
	}

	private static void handlePlayerData(UUID uuid, PlayerDataContext context, byte[] data, BfConnection connection) {
		BfPlayerData playerData = new BfPlayerData(uuid);
		ByteBuf buf = Unpooled.wrappedBuffer(data);

		try {
			playerData.read(context, buf);
		} catch (Exception e) {
			log.error("failed to read player data", e);
			connection.dataCache.playerData.complete(uuid, e);
			return;
		} finally {
			buf.release();
		}

		connection.dataCache.playerData.complete(uuid, playerData);
	}
}
