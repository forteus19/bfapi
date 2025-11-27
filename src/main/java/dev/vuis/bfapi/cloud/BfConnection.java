package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.ConnectionType;
import com.boehmod.bflib.cloud.common.item.CloudItems;
import com.boehmod.bflib.cloud.common.player.achievement.CloudAchievements;
import com.boehmod.bflib.cloud.connection.Connection;
import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import com.boehmod.bflib.cloud.connection.ConnectionStatusContext;
import com.boehmod.bflib.cloud.connection.EncryptedConnectionCredentials;
import com.boehmod.bflib.cloud.encryption.AESDecryptionHandler;
import com.boehmod.bflib.cloud.encryption.AESEncryptionHandler;
import com.boehmod.bflib.cloud.encryption.EncryptionUtils;
import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.primitives.ClientHeartBeatPacket;
import com.boehmod.bflib.cloud.packet.primitives.ClientLoginPacket;
import com.boehmod.bflib.cloud.packet.primitives.ClientLogoutPacket;
import com.boehmod.bflib.cloud.packet.primitives.EncryptionKeyExchangePacket;
import com.boehmod.bflib.cloud.packet.primitives.EncryptionReadyPacket;
import dev.vuis.bfapi.auth.MinecraftAuth;
import dev.vuis.bfapi.cloud.cache.BfDataCache;
import dev.vuis.bfapi.data.MinecraftProfile;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class BfConnection extends Connection<BfPlayerData> {
	private static final int MAX_CONNECT_ATTEMPTS = 5;

	private static final Random SECURE_RANDOM = new SecureRandom();

	public final BfDataCache dataCache = new BfDataCache(this);
	public final CloudRegistry registry = new CloudRegistry();

	private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
	private @Nullable ScheduledFuture<?> heartbeatFuture;

	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

	private final Set<Consumer<ConnectionStatus>> statusListeners = new HashSet<>();

	private final KeyPair clientKeyPair;

	{
		try {
			clientKeyPair = EncryptionUtils.generateECDHKeyPair();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private final SocketAddress address;
	private final MinecraftAuth mcAuth;
	private final MinecraftProfile mcProfile;
	private final String version;
	private final String versionHash;
	private final byte[] hardwareId;

	@Getter
	private @Nullable Channel channel = null;
	private int connectAttempts = 0;

	public BfConnection(SocketAddress address, MinecraftAuth mcAuth, MinecraftProfile mcProfile, String version, String versionHash, byte[] hardwareId) {
		super(30 * 20);
		this.address = address;
		this.mcAuth = mcAuth;
		this.mcProfile = mcProfile;
		this.version = version;
		this.versionHash = versionHash;
		this.hardwareId = hardwareId;

		CloudAchievements.registerAchievements(registry);
		CloudItems.registerItems(registry);
	}

	public void connect() {
		log.info("connecting to cloud at {}", address);

		Bootstrap bootstrap = new Bootstrap()
			.group(new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory()))
			.channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
			.handler(new BfCloudChannelInitializer(this));

		connectAttempts++;
		bootstrap.connect(address).addListener((ChannelFutureListener) channelFuture -> {
			if (channelFuture.isSuccess()) {
				log.info("cloud connection established at {}", address);
				channel = channelFuture.channel();
				sendCredentials();
			} else {
				log.error("failed to connect to cloud at {}", address);
				channel = null;
				reconnect(false);
			}
		});
	}

	public Channel channelOrThrow() {
		if (channel == null) {
			throw new IllegalStateException("channel is null");
		}
		return channel;
	}

	public boolean isConnectedAndVerified() {
		return channel != null && channel.isActive() && getStatus().isVerified();
	}

	private void sendCredentials() throws IOException {
		log.info("sending cloud credentials");

		EncryptedConnectionCredentials credentials = new EncryptedConnectionCredentials(
			getType(),
			mcProfile.uuid(),
			mcProfile.username(),
			version,
			versionHash,
			hardwareId,
			clientKeyPair.getPublic()
		);

		ByteBuf buf = Unpooled.buffer();
		credentials.writeCredentials(buf);
		channelOrThrow().writeAndFlush(buf);
	}

	void handleKeyExchange(EncryptionKeyExchangePacket packet) throws GeneralSecurityException {
		PublicKey serverPublicKey = packet.serverPublicKey();
		SecretKey secretKey = EncryptionUtils.deriveSharedSecret(clientKeyPair.getPrivate(), serverPublicKey);

		ChannelPipeline pipeline = channelOrThrow().pipeline();
		pipeline.addAfter(
			"frameDecoder", "decryption",
			Util.apply(new AESDecryptionHandler(secretKey), AESDecryptionHandler::activateDecryption)
		);
		pipeline.addAfter(
			"frameEncoder", "encryption",
			Util.apply(new AESEncryptionHandler(secretKey, false), AESEncryptionHandler::activateEncryption)
		);

		sendPacket(new EncryptionReadyPacket());

		log.info("cloud encryption established");
	}

	@Override
	public BfPlayerData getPlayerCloudData() {
		throw new AssertionError();
	}

	@Override
	public void onConnectionStatusChanged(@NotNull ConnectionStatus status, @NotNull ConnectionStatusContext context) {
		log.info("cloud connection status changed to {} (context {})", status, context);

		switch (status) {
			case CONNECTED_NOT_VERIFIED -> {
				String serverId = randomServerId();

				log.info("joining session server");
				try {
					mcAuth.joinServer(mcProfile, serverId);
				} catch (IOException | InterruptedException e) {
					log.error("failed to join session server", e);
					disconnect("failed to join session server", true);
				}

				log.info("sending login packet");
				sendPacket(new ClientLoginPacket(serverId, 0));
			}
			case CONNECTED_VERIFIED -> {
				log.info("cloud connection verified");
				connectAttempts = 0;

				if (heartbeatFuture == null) {
					heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(
						this::heartbeat,
						5, 15, TimeUnit.SECONDS
					);
				}

				reconnectExecutor.schedule(
					() -> reconnect(true),
					30, TimeUnit.MINUTES
				);
			}
		}

		for (Consumer<ConnectionStatus> statusListener : statusListeners) {
			statusListener.accept(status);
		}
	}

	private void heartbeat() {
		if (isConnectedAndVerified()) {
			sendPacket(new ClientHeartBeatPacket());
		}
	}

	private void reconnect(boolean connectNow) {
		disconnect("reconnecting", true);

		if (connectAttempts < MAX_CONNECT_ATTEMPTS) {
			if (connectNow) {
				log.info("reconnecting");

				connect();
			} else {
				log.info("reconnecting in 60 seconds");

				reconnectExecutor.schedule(
					this::connect,
					60, TimeUnit.SECONDS
				);
			}
		} else {
			log.error("failed to connect to cloud {} times; stopping", MAX_CONNECT_ATTEMPTS);
		}
	}

	public void addStatusListener(Consumer<ConnectionStatus> statusListener) {
		statusListeners.add(statusListener);
	}

	@Override
	protected void onUpdate() {
		throw new AssertionError();
	}

	@Override
	protected boolean shouldHandlePacket(@NotNull IPacket iPacket) {
		return true;
	}

	@Override
	public void disconnect(@NotNull String reason, boolean sendLogout) {
		if (channel == null || isConnectionClosed()) {
			return;
		}

		log.warn("cloud disconnected: {}", reason);

		if (heartbeatFuture != null) {
			heartbeatFuture.cancel(false);
			heartbeatFuture = null;
		}

		if (sendLogout) {
			sendPacket(new ClientLogoutPacket());
		}

		channel.close();
		channel = null;
	}

	@Override
	public @NotNull UUID getUUID() {
		throw new AssertionError();
	}

	@Override
	public byte[] getHardwareId() {
		throw new AssertionError();
	}

	@Override
	public @NotNull String getUsername() {
		throw new AssertionError();
	}

	@Override
	public @NotNull String getVersion() {
		throw new AssertionError();
	}

	@Override
	public @NotNull String getVersionHash() {
		throw new AssertionError();
	}

	@Override
	public @NotNull ConnectionType getType() {
		return ConnectionType.PLAYER;
	}

	@Override
	public void sendPacket(@NotNull IPacket packet) {
		channelOrThrow().writeAndFlush(packet);
	}

	@Override
	public <T extends IPacket> void onIllegalPacket(@NotNull T packet, @NotNull ConnectionType actualType, @NotNull ConnectionType expectedType) {
		log.error("illegal packet {} (expected: {}, actual: {})", packet.getClass().getSimpleName(), expectedType, actualType);
	}

	private static String randomServerId() {
		byte[] bytes = new byte[20];
		SECURE_RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
