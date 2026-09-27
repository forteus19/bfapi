package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItemStack;
import com.boehmod.bflib.cloud.common.item.CloudItemType;
import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.util.Util;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BfPlayerInventory {
	public final Map<UUID, CloudItemStack> items = new Object2ObjectOpenHashMap<>();
	public final Set<UUID> equipped = new ObjectOpenHashSet<>();
	public final Set<UUID> showcased = new ObjectOpenHashSet<>();

	public BfPlayerInventory() {
	}

	public BfPlayerInventory(
		Map<UUID, CloudItemStack> items,
		Set<UUID> equipped,
		Set<UUID> showcased
	) {
		this.items.putAll(items);
		this.equipped.addAll(equipped);
		this.showcased.addAll(showcased);
	}

	public void putItems(Map<UUID, CloudItemStack> items) {
		this.items.putAll(items);
	}

	public static Map<UUID, CloudItemStack> itemMap(List<CloudItemStack> stacks) {
		return stacks.stream().collect(Collectors.toMap(
			CloudItemStack::getUUID,
			Function.identity()
		));
	}

	public @NotNull JsonWriter serialize(
		@NotNull JsonWriter w,
		@NotNull Target target,
		@NotNull CloudRegistry registry,
		boolean includeUuid,
		boolean includeDetails,
		@Nullable Consumer<JsonWriter> extra
	) throws IOException {
		w.beginObject();

		w.name(target.rootName).beginArray();
		for (CloudItemStack stack : items.values()) {
			if (!target.filter.test(this, stack)) {
				continue;
			}

			CloudItem<?> item = stack.getCloudItem(registry);
			assert item != null;

//			if (!item.isDefault()) {
			cloudItemStack(w, stack, item, includeUuid, includeDetails);
//			}
		}
		w.endArray();

		if (extra != null) {
			extra.accept(w);
		}

		w.endObject();

		return w;
	}

	private static @NotNull JsonWriter cloudItemStack(
		@NotNull JsonWriter w,
		@NotNull CloudItemStack stack,
		@NotNull CloudItem<?> item,
		boolean includeUuid,
		boolean includeDetails
	) throws IOException {
		w.beginObject();

		if (includeUuid) {
			w.name("uuid").value(Util.getBase64Uuid(stack.getUUID()));
		}
		w.name("id").value(stack.getItemId());
		if (includeDetails) {
			CloudItemType type = item.getItemType();
			w.name("name").value(
				item.isDefault() && type != CloudItemType.CARD ?
					item.getName() :
					item.getDisplayName()
			);
			w.name("rarity").value(item.getRarity().name().toLowerCase(Locale.ROOT));
			w.name("type").value(type.name().toLowerCase(Locale.ROOT));
		}
		w.name("mint").value(stack.getMint());

		w.endObject();

		return w;
	}

	public enum Target {
		ALL("inventory", (_, _) -> true),
		EQUIPPED("equipped", (inventory, stack) -> inventory.equipped.contains(stack.getUUID())),
		SHOWCASED("showcased", (inventory, stack) -> inventory.showcased.contains(stack.getUUID()));

		private final String rootName;
		private final BiPredicate<BfPlayerInventory, CloudItemStack> filter;

		Target(String rootName, BiPredicate<BfPlayerInventory, CloudItemStack> filter) {
			this.rootName = rootName;
			this.filter = filter;
		}
	}
}
