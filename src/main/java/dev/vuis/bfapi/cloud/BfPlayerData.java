package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.AbstractPlayerCloudData;
import com.boehmod.bflib.cloud.common.player.PlayerGroup;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BfPlayerData extends AbstractPlayerCloudData<BfPlayerInventory> {
    private @Nullable PlayerGroup group;

    public BfPlayerData(@NotNull UUID uuid) {
        super(uuid);
    }

    @Override
    public @Nullable AbstractClanData getClanData() {
        throw new AssertionError();
    }

    @Override
    protected @NotNull BfPlayerInventory createInventory() {
        return new BfPlayerInventory(this);
    }

    @Override
    public @NotNull Optional<PlayerGroup> getGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public void setGroup(@Nullable PlayerGroup playerGroup) {
        group = playerGroup;
    }
}
