package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.ConnectionType;
import com.boehmod.bflib.cloud.connection.Connection;
import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import com.boehmod.bflib.cloud.connection.ConnectionStatusContext;
import com.boehmod.bflib.cloud.connection.EncryptedConnectionCredentials;
import com.boehmod.bflib.cloud.packet.IPacket;
import com.boehmod.bflib.cloud.packet.primitives.ClientLogoutPacket;
import dev.vuis.bfapi.data.MinecraftProfile;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class BfConnection extends Connection<BfPlayerData> {
    private final MinecraftProfile mcProfile;
    private final String version;
    private final String versionHash;
    private final byte[] hardwareId;

    private @Nullable Channel channel = null;

    public BfConnection(MinecraftProfile mcProfile, String version, String versionHash, byte[] hardwareId) {
        super(30 * 20);
        this.mcProfile = mcProfile;
        this.version = version;
        this.versionHash = versionHash;
        this.hardwareId = hardwareId;
    }

    public void connect(SocketAddress address) {
        log.info("connecting to cloud at {}", address);

        Bootstrap bootstrap = new Bootstrap()
            .group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()))
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
            .handler(new BfCloudChannelInitializer());

        bootstrap.connect(address).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                log.info("cloud connection established at {}", address);
                channel = channelFuture.channel();
                sendCredentials();
            } else {
                log.info("failed to connect to cloud at {}", address);
                channel = null;
            }
        });
    }

    public Channel channelOrThrow() {
        if (channel == null) {
            throw new IllegalStateException("channel is null");
        }
        return channel;
    }

    private void sendCredentials() throws IOException {
        log.info("sending cloud credentials");

        EncryptedConnectionCredentials credentials = new EncryptedConnectionCredentials(
            ConnectionType.PLAYER,
            mcProfile.uuid(),
            mcProfile.username(),
            version,
            versionHash,
            hardwareId,
            null
        );

        ByteBuf buf = Unpooled.buffer();
        credentials.writeCredentials(buf);
        channelOrThrow().writeAndFlush(buf);
    }

    @Override
    public BfPlayerData getPlayerCloudData() {
        throw new AssertionError();
    }

    @Override
    public void onConnectionStatusChanged(@NotNull ConnectionStatus status, @NotNull ConnectionStatusContext context) {
        log.info("cloud connection status changed to {} (context {})", status, context);
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
    public void disconnect(@NotNull String s, boolean b) {
        if (isConnectionClosed() || channel == null) {
            return;
        }
        sendPacket(new ClientLogoutPacket());
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
        throw new AssertionError();
    }

    @Override
    public void sendPacket(@NotNull IPacket packet) {
        channelOrThrow().writeAndFlush(packet);
    }

    @Override
    public <T extends IPacket> void onIllegalPacket(@NotNull T packet, @NotNull ConnectionType actualType, @NotNull ConnectionType expectedType) {
        log.error("illegal packet {} (expected: {}, actual: {})", packet.getClass().getSimpleName(), expectedType, actualType);
    }
}
