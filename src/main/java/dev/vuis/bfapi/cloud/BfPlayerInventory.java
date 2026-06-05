package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.CloudRegistry;
import com.boehmod.bflib.cloud.common.item.CloudItem;
import com.boehmod.bflib.cloud.common.item.CloudItemStack;
import com.boehmod.bflib.cloud.common.item.CloudItemType;
import com.boehmod.bflib.cloud.common.player.AbstractCloudInventory;
import com.google.gson.stream.JsonWriter;
import dev.vuis.bfapi.util.Util;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BfPlayerInventory extends AbstractCloudInventory<BfPlayerData> {
	public BfPlayerInventory() {
		//noinspection DataFlowIssue
		super(null);
	}

	public @NotNull JsonWriter serialize(@NotNull JsonWriter w, @NotNull CloudRegistry registry, boolean includeUuid, boolean includeDetails, @Nullable Consumer<JsonWriter> extra) throws IOException {
		w.beginObject();

		w.name("inventory").beginArray();
		for (CloudItemStack itemStack : getItems()) {
			CloudItem<?> item = itemStack.getCloudItem(registry);
			assert item != null;

//			if (!item.isDefault()) {
			cloudItemStack(w, itemStack, item, includeUuid, includeDetails);
//			}
		}
		w.endArray();

		if (extra != null) {
			extra.accept(w);
		}

		w.endObject();

		return w;
	}

	private static @NotNull JsonWriter cloudItemStack(@NotNull JsonWriter w, @NotNull CloudItemStack stack, @NotNull CloudItem<?> item, boolean includeUuid, boolean includeDetails) throws IOException {
		w.beginObject();

		if (includeUuid) {
			w.name("uuid").value(Util.getBase64Uuid(stack.getUUID()));
		}
		w.name("id").value(stack.getItemId());
		if (includeDetails) {
			CloudItemType type = item.getItemType();
			w.name("name").value(
				item.isDefault() && type != CloudItemType.ARMOR && type != CloudItemType.CARD ?
					item.getName() :
					item.getDisplayName()
			);
			w.name("rarity").value(item.getRarity().name().toLowerCase(Locale.ROOT));
			w.name("type").value(type.name().toLowerCase(Locale.ROOT));
		}
		w.name("mint").value(stack.getMint());
		Optional<String> nameTag = stack.getNameTag();
		if (nameTag.isPresent()) {
			w.name("tag").value(nameTag.orElseThrow());
		}

		w.endObject();

		return w;
	}
}
