package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.player.AbstractCloudInventory;
import org.jetbrains.annotations.NotNull;

public class BfPlayerInventory extends AbstractCloudInventory<BfPlayerData> {
	public BfPlayerInventory(@NotNull BfPlayerData playerData) {
		super(playerData);
	}
}
