package com.bgsoftware.superiorskyblock.nms.v1_18_R1.mapping.net.minecraft.core;

import com.bgsoftware.common.remaps.Remap;
import net.minecraft.core.IRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.world.item.Item;

public final class RegistryBlocks {

    @Remap(classPath = "net.minecraft.core.Registry", name = "ITEM", type = Remap.Type.FIELD, remappedName = "aa")
    public static final net.minecraft.core.RegistryBlocks<Item> ITEM_REGISTRY = IRegistry.aa;

    @Remap(classPath = "net.minecraft.core.Registry",
            name = "getKey",
            type = Remap.Type.METHOD,
            remappedName = "b")
    public static MinecraftKey getKey(net.minecraft.core.RegistryBlocks<Item> registryBlocks, Item item) {
        return registryBlocks.b(item);
    }

}
