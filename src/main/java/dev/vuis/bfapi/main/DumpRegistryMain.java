package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.item.ActivatedCloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItems;
import com.boehmod.bflib.cloud.common.item.pattern.SkinPattern;
import com.boehmod.bflib.cloud.common.item.types.CloudItemArmour;
import com.boehmod.bflib.cloud.common.item.types.CloudItemBooster;
import com.boehmod.bflib.cloud.common.item.types.CloudItemCase;
import com.boehmod.bflib.cloud.common.item.types.CloudItemGun;
import com.boehmod.bflib.cloud.common.player.BoosterType;
import com.boehmod.bflib.cloud.common.player.achievement.CloudAchievement;
import com.boehmod.bflib.cloud.common.player.achievement.CloudAchievements;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.util.ReflectionUtil;
import dev.vuis.bfapi.util.Util;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DumpRegistryMain {
	private DumpRegistryMain() {
	}

	@SneakyThrows
	public static void main(String[] args) {
		log.info("dumping cloud registry");

		CloudRegistry registry = new CloudRegistry();
		CloudAchievements.registerAchievements(registry);
		CloudItems.registerItems(registry);

		JsonObject root = new JsonObject();

		root.add("achievements", Util.apply(new JsonObject(), achievements -> {
			for (CloudAchievement achievement : registry.getAchievements()) {
				achievements.add(Integer.toString(achievement.getId()), Util.apply(new JsonObject(), achievementRoot -> {
					achievementRoot.addProperty("name", achievement.getName());
					achievementRoot.addProperty("description", achievement.getDescription());
				}));
			}
		}));

		root.add("items", Util.apply(new JsonObject(), items -> {
			for (CloudItem<?> item : registry.getItems()) {
				items.add(Integer.toString(item.getId()), Util.apply(new JsonObject(), itemRoot -> {
					itemRoot.addProperty("name", item.getName());
					itemRoot.addProperty("suffix", item.getSuffix());
					itemRoot.addProperty("rarity", item.getRarity().toString().toLowerCase());
					if (item.getCollection() != null) {
						itemRoot.addProperty("collection", item.getCollection());
					}
					if (item.getSkin() != 0f) {
						itemRoot.addProperty("skin_id", item.getSkin());
					}
					itemRoot.addProperty("type", item.getItemType().toString().toLowerCase());

					if (item instanceof ActivatedCloudItem<?> activatedItem) {
						activatedItem.getActivationAchievement().ifPresent(achievement ->
							itemRoot.addProperty("activation_achievement", achievement.getId())
						);
					}

					switch (item) {
						case CloudItemArmour itemArmour -> {
							itemRoot.addProperty("nation", itemArmour.getNation().getTag());
						}
						case CloudItemCase itemCase -> {
							itemRoot.addProperty("case_key", itemCase.key.getId());
						}
						case CloudItemGun itemGun -> {
							if (itemGun.hasPatternSkin()) {
								SkinPattern skinPattern = itemGun.getPatternSkin();
								assert skinPattern != null;

								itemRoot.add("pattern_skin", Util.apply(new JsonObject(), skinPatternRoot -> {
									skinPatternRoot.addProperty("name", skinPattern.name());
									skinPatternRoot.addProperty("width", skinPattern.width());
									skinPatternRoot.addProperty("height", skinPattern.height());
								}));
							}
						}
						case CloudItemBooster itemBooster -> {
							itemRoot.addProperty("booster_type",
								ReflectionUtil.<BoosterType>getField(itemBooster, "type").toString().toLowerCase()
							);
							itemRoot.addProperty("minutes",
								ReflectionUtil.<Integer>getField(itemBooster, "minutes")
							);
							itemRoot.addProperty("multiplier",
								ReflectionUtil.<Integer>getField(itemBooster, "multiplier")
							);
						}
						default -> {
						}
					}
				}));
			}
		}));

		String jsonStr = Util.gson(false).toJson(root);
		byte[] jsonHash = MessageDigest.getInstance("MD5").digest(jsonStr.getBytes(StandardCharsets.UTF_8));

		Files.writeString(Path.of("registry.json"), jsonStr);
		Files.writeString(Path.of("registry_hash.txt"), Util.createHexString(jsonHash));
	}
}
