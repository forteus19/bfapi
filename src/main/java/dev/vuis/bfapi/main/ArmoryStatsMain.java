package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItemStack;
import com.boehmod.bflib.cloud.common.item.CloudItemType;
import dev.vuis.bfapi.cloud.BfCloudPacketHandlers;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.BfPlayerInventory;
import dev.vuis.bfapi.data.BfApiConfig;
import dev.vuis.bfapi.util.AuthUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;

@Slf4j
public final class ArmoryStatsMain {
	private ArmoryStatsMain() {
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

		BfCloudPacketHandlers.registerPrimitive();
		BfCloudPacketHandlers.registerInfo();
		BfCloudPacketHandlers.registerData();

		@Cleanup
		BfConnection connection = new BfConnection(
			config.getBfCloudAddress(),
			config.getBfVersion(),
			config.getBfVersionHash(),
			config.getBfHardwareId(),
			authManager,
			config.getHttpUserAgent(),
			_ -> null
		);
		connection.connect();

		CompletableFuture<Void> verifiedFuture = new CompletableFuture<>();
		connection.addStatusListener((_, status) -> {
			if (status.isVerified()) {
				verifiedFuture.complete(null);
			}
		});
		verifiedFuture.join();

		BfPlayerInventory inventory = connection.dataCache.playerInventory.get(mcProfile.getId()).get(10, TimeUnit.SECONDS).value();

		IntSet visited = new IntOpenHashSet();

		int skins = 0;
		List<CloudItem<?>> dupes = new ObjectArrayList<>();

		for (CloudItemStack stack : inventory.getItems()) {
			int id = stack.getItemId();
			CloudItem<?> item = stack.getCloudItem(connection.registry);
			if (item == null) {
				continue;
			}

			CloudItemType type = item.getItemType();
			if (!item.isDefault() && (type == CloudItemType.GUN || type == CloudItemType.MELEE)) {
				skins++;
			}

			if (!visited.add(id)) {
				dupes.add(item);
			}
		}

		log.info("skins: {}/256", skins);

		log.info("duplicates:");
		for (CloudItem<?> dup : dupes) {
			log.info("- {}", dup.getDisplayName());
		}

		connection.disconnect("finished", true);
		System.exit(0);
	}
}
