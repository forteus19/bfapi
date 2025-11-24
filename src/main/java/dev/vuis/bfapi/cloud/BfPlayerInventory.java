package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.player.AbstractCloudInventory;

public class BfPlayerInventory extends AbstractCloudInventory<BfPlayerData> {
	public BfPlayerInventory() {
		// shut up it can be null
		super(null);
	}
}
