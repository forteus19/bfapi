package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import com.boehmod.bflib.cloud.packet.common.mm.PacketMMJoinGameFromSecret;
import com.boehmod.bflib.cloud.packet.common.mm.PacketMMJoinServer;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.data.BfApiConfig;
import dev.vuis.bfapi.util.AuthUtil;
import java.net.InetSocketAddress;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;

@Slf4j
public final class JoinMatchMain {
	private static UUID matchUuid;

	private JoinMatchMain() {
	}

	@SneakyThrows
	static void main() {
		BfApiConfig config = BfApiConfig.instance();

		HttpClient authHttpClient = MinecraftAuth.createHttpClient(config.getHttpUserAgent());
		JavaAuthManager authManager = AuthUtil.tryLoadAuthJson(authHttpClient, config.getTokensJsonPath());
		if (authManager == null) {
			authManager = AuthUtil.createAuthManagerFromLogin(authHttpClient);
		}

		log.info("retrieving profile");
		MinecraftProfile mcProfile = authManager.getMinecraftProfile().getUpToDate();

		log.info("authenticated as {} ({})", mcProfile.getName(), mcProfile.getId());
		log.info("press enter to continue");
		IO.readln();

		log.info("saving auth tokens");
		AuthUtil.saveAuthJson(authManager, config.getTokensJsonPath());

		log.info("enter match UUID:");
		matchUuid = UUID.fromString(IO.readln());

		BfCloudPacketHandlers.registerInfo();
		BfCloudPacketHandlers.registerPacketHandler(PacketMMJoinServer.class, JoinMatchMain::handleJoinServerPacket);

		@SuppressWarnings("resource")
		BfConnection connection = new BfConnection(
			config.getBfCloudAddress(),
			config.getBfVersion(),
			config.getBfVersionHash(),
			config.getBfHardwareId(),
			authManager
		);
		connection.connect();

		connection.addStatusListener(JoinMatchMain::onConnectionStatusChanged);
	}

	private static void onConnectionStatusChanged(BfConnection connection, ConnectionStatus status) {
		if (status == ConnectionStatus.CONNECTED_VERIFIED) {
			connection.sendPacket(new PacketMMJoinGameFromSecret(matchUuid));
		}
	}

	private static void handleJoinServerPacket(PacketMMJoinServer packet, BfConnection connection) {
		InetSocketAddress address = packet.address();
		String addressStr = address.getAddress().getHostAddress() + ":" + address.getPort();

		log.info("MATCH ADDRESS: {}", addressStr);

		connection.disconnect("match address received", true);
		System.exit(0);
	}
}
