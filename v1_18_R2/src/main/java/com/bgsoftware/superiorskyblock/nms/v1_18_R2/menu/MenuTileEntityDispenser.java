package com.bgsoftware.superiorskyblock.nms.v1_18_R2.menu;

import com.bgsoftware.common.remaps.Remap;
import com.bgsoftware.superiorskyblock.nms.v1_18_R2.mapping.net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.world.level.block.entity.TileEntityDispenser;
import org.bukkit.inventory.InventoryHolder;

public final class MenuTileEntityDispenser extends TileEntityDispenser {

    private final InventoryHolder holder;

    @Remap(classPath = "net.minecraft.world.level.block.entity.BaseContainerBlockEntity", name = "setCustomName", type = Remap.Type.METHOD, remappedName = "a")
    @Remap(classPath = "net.minecraft.network.chat.Component", name = "nullToEmpty", type = Remap.Type.METHOD, remappedName = "a")
    public MenuTileEntityDispenser(InventoryHolder holder, String title) {
        super(BlockPosition.b, Block.AIR.getBlockData().getHandle());
        this.holder = holder;
        this.a(IChatBaseComponent.a(title));
    }

    @Override
    public InventoryHolder getOwner() {
        return holder;
    }

}
