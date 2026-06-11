package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItemType;
import com.boehmod.bflib.cloud.common.item.CloudItems;
import com.boehmod.bflib.cloud.common.item.CloudResourceLocation;
import com.boehmod.bflib.cloud.common.item.pattern.SkinPattern;
import com.boehmod.bflib.cloud.common.item.pattern.SkinPatterns;
import com.boehmod.bflib.cloud.common.item.types.CloudItemGun;
import com.boehmod.bflib.cloud.common.player.achievement.CloudAchievement;
import com.boehmod.bflib.cloud.common.player.achievement.CloudAchievements;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.util.Util;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DumpRegistryMain {
	private static final Map<SkinPattern, Integer> PATTERN_IDS = ImmutableMap.<SkinPattern, Integer>builder()
		.put(SkinPatterns.SPLITTER, 0)
		.put(SkinPatterns.SNOW_STRIPE, 1)
		.put(SkinPatterns.RUST, 2)
		.put(SkinPatterns.CHROMA, 3)
		.put(SkinPatterns.CASE_HARDENED, 5)
		.put(SkinPatterns.CACHEE, 6)
		.put(SkinPatterns.FROGSKIN, 7)
		.put(SkinPatterns.GASCAN, 8)
		.put(SkinPatterns.HEAT_TREATED, 9)
		.put(SkinPatterns.MIMETICO, 10)
		.put(SkinPatterns.PLATANEN, 11)
		.put(SkinPatterns.TTSMKK, 12)
		.buildOrThrow();

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

		Set<CloudResourceLocation> mcIds = new LinkedHashSet<>();
		int mcIdIndex = -1;

		JsonObject detailsRoot = new JsonObject();

		for (CloudItem<?> item : registry.getItems()) {
//			JsonObject itemRoot = new JsonObject();
			JsonArray itemRoot = new JsonArray();

			CloudItemType type = item.getItemType();
			String name;
			if (!item.getSuffix().isEmpty() && !(item.isDefault() && type != CloudItemType.ARMOR && type != CloudItemType.CARD)) {
				name = item.getName() + " " + item.getSuffix();
			} else {
				name = item.getName();
			}

//			itemRoot.addProperty("name", name);
//			itemRoot.addProperty("rarity", item.getRarity().name());
//			itemRoot.addProperty("type", type.name());

			itemRoot.add(name);
			itemRoot.add(item.getRarity().ordinal());
			itemRoot.add(type.ordinal());
			if (type == CloudItemType.GUN && item instanceof CloudItemGun itemGun) {
				if (mcIds.add(itemGun.getMinecraftItem())) {
					mcIdIndex++;
				}
				itemRoot.add(mcIdIndex);

				SkinPattern pattern = itemGun.getPatternSkin();
				if (pattern != null) {
					itemRoot.add(PATTERN_IDS.get(pattern));
				}
			}

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

			detailsRoot.add(Integer.toString(item.getId()), itemRoot);
		}

		itemsRoot.add("details", detailsRoot);

		JsonArray mcIdsRoot = new JsonArray(mcIds.size());

		for (CloudResourceLocation id : mcIds) {
			mcIdsRoot.add(id.toString());
		}

		itemsRoot.add("mc_ids", mcIdsRoot);

		JsonArray patternsRoot = new JsonArray(PATTERN_IDS.size());

		for (SkinPattern pattern : PATTERN_IDS.keySet()) {
			patternsRoot.add(pattern.name());
		}

		itemsRoot.add("patterns", patternsRoot);

		try (BufferedWriter writer = Files.newBufferedWriter(Path.of("registry_items.json"))) {
			Util.gson(false).toJson(itemsRoot, writer);
		}
	}
}
