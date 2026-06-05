package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.item.ActivatedCloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItemType;
import com.boehmod.bflib.cloud.common.item.CloudItems;
import com.boehmod.bflib.cloud.common.item.pattern.SkinPattern;
import com.boehmod.bflib.cloud.common.item.types.CloudItemArmour;
import com.boehmod.bflib.cloud.common.item.types.CloudItemBooster;
import com.boehmod.bflib.cloud.common.item.types.CloudItemCase;
import com.boehmod.bflib.cloud.common.item.types.CloudItemGun;
import com.boehmod.bflib.cloud.common.player.BoosterType;
import com.boehmod.bflib.cloud.common.player.achievement.CloudAchievement;
import com.boehmod.bflib.cloud.common.player.achievement.CloudAchievements;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.util.ReflectionUtil;
import dev.vuis.bfapi.util.Util;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DumpRegistryMain {
	private DumpRegistryMain() {
	}

	@SneakyThrows
	static void main() {
		log.info("dumping cloud registry");

		CloudRegistry registry = new CloudRegistry();
		CloudAchievements.registerAchievements(registry);
		CloudItems.registerItems(registry);

		JsonObject achievementsRoot = new JsonObject();

		for (CloudAchievement achievement : registry.getAchievements()) {
			JsonObject achievementRoot = new JsonObject();

			achievementRoot.addProperty("key", achievement.getTranslationKey());

			achievementsRoot.add(Integer.toString(achievement.getId()), achievementRoot);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(Path.of("registry_achievements.json"))) {
			Util.gson(false).toJson(achievementsRoot, writer);
		}

		JsonObject itemsRoot = new JsonObject();

		for (CloudItem<?> item : registry.getItems()) {
			JsonArray itemRoot = new JsonArray();

			CloudItemType type = item.getItemType();
			String name;
			if (!item.getSuffix().isEmpty() && !(item.isDefault() && type != CloudItemType.ARMOR && type != CloudItemType.CARD)) {
				name = item.getName() + " " + item.getSuffix();
			} else {
				name = item.getName();
			}

			itemRoot.add(name);
			itemRoot.add(item.getRarity().ordinal());
			itemRoot.add(type.ordinal());

//			if (item.getCollection() != null) {
//				itemRoot.addProperty("collection", item.getCollection());
//			}
//			if (item.getSkin() != 0f) {
//				itemRoot.addProperty("f", item.getSkin());
//			}

//			if (item instanceof ActivatedCloudItem<?> activatedItem) {
//				activatedItem.getActivationAchievement().ifPresent(achievement ->
//					itemRoot.addProperty("a", achievement.getId())
//				);
//			}

//			switch (item) {
//				case CloudItemArmour itemArmour -> {
//					itemRoot.addProperty("nation", itemArmour.getNation().getTag());
//				}
//				case CloudItemCase itemCase -> {
//					itemRoot.addProperty("case_key", itemCase.key.getId());
//				}
//				case CloudItemGun itemGun -> {
//					if (itemGun.hasPatternSkin()) {
//						SkinPattern skinPattern = itemGun.getPatternSkin();
//						assert skinPattern != null;
//
//						itemRoot.addProperty("pattern", skinPattern.name());
//					}
//				}
//				case CloudItemBooster itemBooster -> {
//					itemRoot.addProperty("booster_type",
//						ReflectionUtil.<BoosterType>getField(itemBooster, "type").name().toLowerCase(Locale.ROOT)
//					);
//					itemRoot.addProperty("minutes",
//						ReflectionUtil.<Integer>getField(itemBooster, "minutes")
//					);
//					itemRoot.addProperty("multiplier",
//						ReflectionUtil.<Integer>getField(itemBooster, "multiplier")
//					);
//				}
//				default -> {
//				}
//			}

			itemsRoot.add(Integer.toString(item.getId()), itemRoot);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(Path.of("registry_items.json"))) {
			Util.gson(false).toJson(itemsRoot, writer);
		}
	}
}
