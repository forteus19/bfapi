package dev.vuis.bfapi.main;

import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItemRarity;
import com.boehmod.bflib.cloud.common.item.CloudItemType;
import com.boehmod.bflib.cloud.common.item.CloudItems;
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
	private static final Map<String, McDetails> MC_DETAILS = ImmutableMap.<String, McDetails>builderWithExpectedSize(93)
		.put("gun_tokarev_avt40", new McDetails("gun_tokarev_avt40", 20, 60))
		.put("gun_tokarev_svt40", new McDetails("gun_tokarev_svt40", 10, 30))
		.put("gun_type92", new McDetails("gun_type92", 16, 192))
		.put("gun_welrod", new McDetails("gun_welrod", 8, 24))
		.put("gun_brownbess", new McDetails("gun_brownbess", 1, 16))
		.put("gun_browning30", new McDetails("gun_browning30", 150, 600))
		.put("gun_mg42", new McDetails("gun_mg42", 250, 250))
		.put("gun_vickers_k", new McDetails("gun_vickers_k", 100, 300))
		.put("gun_fiat_revelli", new McDetails("gun_fiat_revelli", 150, 300))
		.put("gun_mac_mle_1931", new McDetails("gun_mac_mle_1931", 150, 300))
		.put("gun_lewisgun", new McDetails("gun_lewisgun", 97, 291))
		.put("gun_bar", new McDetails("gun_bar", 20, 60))
		.put("gun_dp28", new McDetails("gun_dp28", 47, 141))
		.put("gun_mg34", new McDetails("gun_mg34", 150, 300))
		.put("gun_zb26", new McDetails("gun_zb26", 20, 60))
		.put("gun_type11", new McDetails("gun_type11", 30, 90))
		.put("gun_bren_mk2", new McDetails("gun_bren_mk2", 30, 90))
		.put("gun_model_1930", new McDetails("gun_model_1930", 20, 60))
		.put("gun_breda_safat", new McDetails("gun_breda_safat", 150, 300))
		.put("gun_type96", new McDetails("gun_type96", 30, 90))
		.put("gun_type98", new McDetails("gun_type98", 75, 150))
		.put("gun_m1928a1_thompson", new McDetails("gun_m1928a1_thompson", 20, 100))
		.put("gun_m1a1_thompson", new McDetails("gun_m1a1_thompson", 30, 120))
		.put("gun_greasegun", new McDetails("gun_greasegun", 30, 90))
		.put("gun_mp40", new McDetails("gun_mp40", 32, 96))
		.put("gun_stg44", new McDetails("gun_stg44", 30, 90))
		.put("gun_blyskawica", new McDetails("gun_blyskawica", 20, 60))
		.put("gun_kop_pal", new McDetails("gun_kop_pal", 32, 96))
		.put("gun_pps43", new McDetails("gun_pps43", 35, 105))
		.put("gun_ppsh", new McDetails("gun_ppsh", 35, 105))
		.put("gun_type100", new McDetails("gun_type100", 30, 90))
		.put("gun_sten_mk2", new McDetails("gun_sten_mk2", 32, 96))
		.put("gun_model_38", new McDetails("gun_model_38", 30, 90))
		.put("gun_mas_38", new McDetails("gun_mas_38", 32, 96))
		.put("gun_ak74", new McDetails("gun_ak74", 30, 90))
		.put("gun_m4a4", new McDetails("gun_m4a4", 30, 90))
		.put("gun_fg42", new McDetails("gun_fg42", 20, 60))
		.put("gun_trenchgun", new McDetails("gun_trenchgun", 6, 18))
		.put("gun_m30", new McDetails("gun_m30", 2, 20))
		.put("gun_becker", new McDetails("gun_becker", 5, 15))
		.put("gun_mauser_m712", new McDetails("gun_mauser_m712", 20, 60))
		.put("gun_springfield", new McDetails("gun_springfield", 5, 15))
		.put("gun_kar98k", new McDetails("gun_kar98k", 5, 15))
		.put("gun_kbk_wz_29", new McDetails("gun_kbk_wz_29", 5, 15))
		.put("gun_mosin_nagant", new McDetails("gun_mosin_nagant", 5, 15))
		.put("gun_type38", new McDetails("gun_type38", 5, 15))
		.put("gun_type99", new McDetails("gun_type99", 5, 15))
		.put("gun_lee_enfield_mk1", new McDetails("gun_lee_enfield_mk1", 10, 30))
		.put("gun_carcano_m91ts_carbine", new McDetails("gun_carcano_m91ts_carbine", 6, 18))
		.put("gun_carcano_m38", new McDetails("gun_carcano_m38", 6, 18))
		.put("gun_lebel_1886", new McDetails("gun_lebel_1886", 10, 30))
		.put("gun_m1_garand", new McDetails("gun_m1_garand", 8, 24))
		.put("gun_m1_carbine", new McDetails("gun_m1_carbine", 15, 60))
		.put("gun_m2_carbine", new McDetails("gun_m2_carbine", 30, 90))
		.put("gun_gewehr_43", new McDetails("gun_gewehr_43", 10, 30))
		.put("gun_type4", new McDetails("gun_type4", 10, 30))
		.put("gun_lee_enfield_turner", new McDetails("gun_lee_enfield_turner", 10, 30))
		.put("gun_model_1935", new McDetails("gun_model_1935", 20, 60))
		.put("gun_fusil_1917", new McDetails("gun_fusil_1917", 5, 30))
		.put("gun_howell", new McDetails("gun_howell", 10, 30))
		.put("gun_ptrs", new McDetails("gun_ptrs", 5, 5))
		.put("gun_batr", new McDetails("gun_batr", 5, 5))
		.put("gun_panzerbuchse39", new McDetails("gun_panzerbuchse39", 1, 10))
		.put("gun_type26", new McDetails("gun_type26", 6, 18))
		.put("gun_webley_mk6", new McDetails("gun_webley_mk6", 6, 18))
		.put("gun_modele_1892_revolver", new McDetails("gun_modele_1892_revolver", 6, 18))
		.put("gun_colt", new McDetails("gun_colt", 7, 21))
		.put("gun_beretta_m1934", new McDetails("gun_beretta_m1934", 7, 21))
		.put("gun_fn_model_1910", new McDetails("gun_fn_model_1910", 8, 24))
		.put("gun_walther_p38", new McDetails("gun_walther_p38", 8, 24))
		.put("gun_luger", new McDetails("gun_luger", 8, 24))
		.put("gun_mauser_c96", new McDetails("gun_mauser_c96", 10, 30))
		.put("gun_tokarev_tt33", new McDetails("gun_tokarev_tt33", 8, 24))
		.put("gun_fb_vis", new McDetails("gun_fb_vis", 8, 24))
		.put("gun_type14", new McDetails("gun_type14", 6, 18))
		.put("gun_type94", new McDetails("gun_type94", 6, 18))
		.put("gun_glisenti_model_1910", new McDetails("gun_glisenti_model_1910", 7, 21))
		.put("gun_pistolet_automatique_modele_1935a", new McDetails("gun_pistolet_automatique_modele_1935a", 8, 24))
		.put("gun_browning_hipower", new McDetails("gun_browning_hipower", 13, 39))
		.put("gun_bazooka", new McDetails("gun_bazooka", 1, 4))
		.put("gun_panzerschreck", new McDetails("gun_panzerschreck", 1, 4))
		.put("gun_panzerfaust", new McDetails("gun_panzerfaust", 1, 4))
		.put("gun_piat", new McDetails("gun_piat", 1, 4))
		.put("gun_melon_cannon", new McDetails("gun_melon_cannon", 1, 32))
		.put("gun_kis", new McDetails("gun_kis", 32, 96))
		.put("gun_mp_3008", new McDetails("gun_mp_3008", 32, 96))
		.put("gun_winchester_1895", new McDetails("gun_winchester_1895", 5, 20))
		.put("gun_mp41", new McDetails("gun_mp41", 32, 96))
		.put("gun_type18_shotgun", new McDetails("gun_type18_shotgun", 1, 18))
		.put("gun_de_lisle_carbine", new McDetails("gun_de_lisle_carbine", 11, 33))
		.put("gun_browning_a5", new McDetails("gun_browning_a5", 5, 15))
		.put("gun_type4_70mm", new McDetails("gun_type4_70mm", 1, 4))
		.put("gun_wz_35", new McDetails("gun_wz_35", 4, 4))
		.buildOrThrow();
	private static final Map<SkinPattern, Integer> PATTERN_IDS = ImmutableMap.<SkinPattern, Integer>builderWithExpectedSize(13)
		.put(SkinPatterns.SPLITTER, 0)
		.put(SkinPatterns.SNOW_STRIPE, 1)
		.put(SkinPatterns.RUST, 2)
		.put(SkinPatterns.CHROMA, 3)
		.put(SkinPatterns.COPPER_DUST, 4)
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

		Set<String> mcIds = new LinkedHashSet<>();
		int mcIdIndex = -1;

		JsonObject detailsRoot = new JsonObject();

		for (CloudItem<?> item : registry.getItems()) {
//			JsonObject itemRoot = new JsonObject();
			JsonArray itemRoot = new JsonArray();

			CloudItemType type = item.getItemType();
			String name;
			if (!item.getSuffix().isEmpty() && !(item.isDefault() && type != CloudItemType.CARD)) {
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
			if (!item.isDeprecated() && type == CloudItemType.GUN && item instanceof CloudItemGun itemGun) {
				if (mcIds.add(itemGun.getMinecraftItem().path())) {
					mcIdIndex++;
				}
				itemRoot.add(mcIdIndex);

				if (item.getRarity() != CloudItemRarity.DEFAULT) {
					itemRoot.add(item.getSkin());

					SkinPattern pattern = itemGun.getPatternSkin();
					if (pattern != null) {
						itemRoot.add(PATTERN_IDS.get(pattern));
					}
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

		JsonArray mcsRoot = new JsonArray(mcIds.size());

		for (String id : mcIds) {
			McDetails mcDetails = MC_DETAILS.get(id);

			JsonArray mcRoot = new JsonArray(3);

			mcRoot.add(mcDetails.id.substring(4));
			mcRoot.add(mcDetails.maxAmmo);
			mcRoot.add(mcDetails.capacity);

			mcsRoot.add(mcRoot);
		}

		itemsRoot.add("mc", mcsRoot);

		JsonArray patternsRoot = new JsonArray(PATTERN_IDS.size());

		for (SkinPattern pattern : PATTERN_IDS.keySet()) {
			JsonArray patternRoot = new JsonArray(3);

			patternRoot.add(pattern.name());
			patternRoot.add(pattern.width());
			patternRoot.add(pattern.height());

			patternsRoot.add(patternRoot);
		}

		itemsRoot.add("patterns", patternsRoot);

		try (BufferedWriter writer = Files.newBufferedWriter(Path.of("registry_items.json"))) {
			Util.gson(false).toJson(itemsRoot, writer);
		}
	}

	private record McDetails(
		String id,
		int maxAmmo,
		int capacity
	) {
	}
}
