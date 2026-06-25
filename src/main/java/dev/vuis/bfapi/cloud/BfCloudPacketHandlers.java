package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.item.CloudItemStack;
import com.boehmod.bflib.cloud.common.player.PlayerDataContext;
import com.boehmod.bflib.cloud.common.player.status.PublicPlayerStatus;
import com.boehmod.bflib.cloud.common.reward.AbstractItemRewardType;
import com.boehmod.bflib.cloud.common.reward.ExpRewardType;
import com.boehmod.bflib.cloud.common.reward.RewardType;
import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.IPacketHandlerFunction;
import com.boehmod.bflib.cloud.packet.PacketRegistry;
import com.boehmod.bflib.cloud.packet.common.PacketChatMessageFromCloud;
import com.boehmod.bflib.cloud.packet.common.PacketClientMessagePopup;
import com.boehmod.bflib.cloud.packet.common.PacketDailyReward;
import com.boehmod.bflib.cloud.packet.common.PacketNotificationFromCloud;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedClanData;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedCloudData;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedInventory;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedInventoryMinimal;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedItemDefault;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedPlayerData;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedPlayerDataSet;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedPlayerStatusSet;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedRecentMatches;
import com.boehmod.bflib.cloud.packet.common.server.PacketServerNotification;
import com.boehmod.bflib.cloud.packet.primitives.CloudHeartBeatPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BfCloudPacketHandlers {
	private BfCloudPacketHandlers() {
	}

	public static void registerPrimitive() {
		registerPacketHandler(CloudHeartBeatPacket.class, BfCloudPacketHandlers::cloudHeartBeat);
	}

	public static void registerInfo() {
		registerPacketHandler(PacketChatMessageFromCloud.class, BfCloudPacketHandlers::chatMessageFromCloud);
		registerPacketHandler(PacketClientMessagePopup.class, BfCloudPacketHandlers::clientMessagePopup);
		registerPacketHandler(PacketDailyReward.class, BfCloudPacketHandlers::dailyReward);
		registerPacketHandler(PacketNotificationFromCloud.class, BfCloudPacketHandlers::notificationFromCloud);
		registerPacketHandler(PacketServerNotification.class, BfCloudPacketHandlers::serverNotification);
	}

	public static void registerData() {
		registerPacketHandler(PacketRequestedClanData.class, BfCloudPacketHandlers::requestedClanData);
		registerPacketHandler(PacketRequestedCloudData.class, BfCloudPacketHandlers::requestedCloudData);
		registerPacketHandler(PacketRequestedInventory.class, BfCloudPacketHandlers::requestedInventory);
		registerPacketHandler(PacketRequestedInventoryMinimal.class, BfCloudPacketHandlers::requestedInventoryMinimal);
		registerPacketHandler(PacketRequestedItemDefault.class, BfCloudPacketHandlers::requestedItemDefault);
		registerPacketHandler(PacketRequestedPlayerData.class, BfCloudPacketHandlers::requestedPlayerData);
		registerPacketHandler(PacketRequestedPlayerDataSet.class, BfCloudPacketHandlers::requestedPlayerDataSet);
		registerPacketHandler(PacketRequestedPlayerStatusSet.class, BfCloudPacketHandlers::requestedPlayerStatusSet);
		registerPacketHandler(PacketRequestedRecentMatches.class, BfCloudPacketHandlers::requestedRecentMatches);
	}

	public static <P extends IPacket> void registerPacketHandler(Class<P> packetClass, IPacketHandlerFunction<P, BfConnection> packetHandler) {
		PacketRegistry.registerPacketHandler(packetClass, packetHandler, BfConnection.class);
	}

	private static void chatMessageFromCloud(PacketChatMessageFromCloud packet, BfConnection connection) {
		log.info("cloud chat message: {}", packet.message());
	}

	private static void clientMessagePopup(PacketClientMessagePopup packet, BfConnection connection) {
		log.info("cloud popup message:\n{}\n{}\n{}", packet.type(), packet.title(), packet.message());
	}

	private static void cloudHeartBeat(CloudHeartBeatPacket packet, BfConnection connection) {
		connection.cloudHeartbeat();
	}

	private static void dailyReward(PacketDailyReward packet, BfConnection connection) {
		log.info("login streak: {}", packet.streak());

		for (RewardType reward : packet.rewards()) {
			switch (reward) {
				case ExpRewardType expReward -> {
					log.info("- exp reward: {}", expReward.getAmount());
				}
				case AbstractItemRewardType itemReward -> {
					CloudItemStack stack = itemReward.getGrantedStack();
					if (stack != null) {
						log.info("- item reward: {}", itemReward.getGrantedStack().getDisplayName(connection.registry));
					}
				}
				default -> {
				}
			}
		}
	}

	private static void notificationFromCloud(PacketNotificationFromCloud packet, BfConnection connection) {
		log.info("cloud notification: {}", packet.message());
	}

	private static void requestedClanData(PacketRequestedClanData packet, BfConnection connection) {
		connection.dataCache.clanData.complete(packet.uuid(), packet.clanData());
	}

	private static void requestedCloudData(PacketRequestedCloudData packet, BfConnection connection) {
		connection.dataCache.cloudStats.complete(new BfCloudData(
			packet.getUsersOnline(),
			packet.getGamePlayerCount(),
			Instant.ofEpochMilli(packet.getScoreboardResetTime()),
			createScoreEntryList(packet.getPlayerScores()),
			createScoreEntryList(packet.getClanScores())
		));
	}

	private static List<BfCloudData.ScoreEntry> createScoreEntryList(Object2IntMap<UUID> map) {
		return map.object2IntEntrySet().stream()
			.sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
			.map(BfCloudData.ScoreEntry::of)
			.toList();
	}

	private static void requestedInventory(PacketRequestedInventory packet, BfConnection connection) {
		connection.dataCache.playerInventory.supply(packet.uuid(), inventory -> inventory.onReceiveSection(packet.stacks(), packet.section()));
	}

	private static void requestedInventoryMinimal(PacketRequestedInventoryMinimal packet, BfConnection connection) {
		BfPlayerInventory inventory = new BfPlayerInventory();
		inventory.onReceiveMinimalStacks(packet.stacks());
		connection.dataCache.inventoryMinimal.complete(packet.uuid(), inventory);
	}

	private static void requestedItemDefault(PacketRequestedItemDefault packet, BfConnection connection) {
		connection.dataCache.playerInventoryDefaults.complete(packet.uuid(), packet.itemStacks());
	}

	private static void requestedPlayerData(PacketRequestedPlayerData packet, BfConnection connection) {
		handlePlayerData(packet.uuid(), packet.context(), packet.data(), connection);
	}

	private static void requestedPlayerDataSet(PacketRequestedPlayerDataSet packet, BfConnection connection) {
		for (Map.Entry<UUID, Pair<PlayerDataContext, byte[]>> entry : packet.dataSet().entrySet()) {
			handlePlayerData(entry.getKey(), entry.getValue().left(), entry.getValue().right(), connection);
		}
	}

	private static void requestedPlayerStatusSet(PacketRequestedPlayerStatusSet packet, BfConnection connection) {
		for (Map.Entry<UUID, PublicPlayerStatus> entry : packet.statusSet().entrySet()) {
			connection.dataCache.playerStatus.complete(entry.getKey(), entry.getValue());
		}
	}

	private static void requestedRecentMatches(PacketRequestedRecentMatches packet, BfConnection connection) {
		connection.dataCache.playerMatches.complete(packet.player(), packet.matches());
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
