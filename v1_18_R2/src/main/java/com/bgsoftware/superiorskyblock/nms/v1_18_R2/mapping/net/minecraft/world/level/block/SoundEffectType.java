package com.bgsoftware.superiorskyblock.nms.v1_18_R2.mapping.net.minecraft.world.level.block;

import com.bgsoftware.common.remaps.Remap;
import com.bgsoftware.superiorskyblock.nms.v1_18_R2.mapping.MappedObject;
import net.minecraft.sounds.SoundEffect;

public final class SoundEffectType extends MappedObject<net.minecraft.world.level.block.SoundEffectType> {

    public SoundEffectType(net.minecraft.world.level.block.SoundEffectType handle) {
        super(handle);
    }

    @Remap(classPath = "net.minecraft.world.level.block.SoundType",
            name = "getPlaceSound",
            type = Remap.Type.METHOD,
            remappedName = "e")
    public SoundEffect getPlaceSound() {
        return handle.e();
    }

    @Remap(classPath = "net.minecraft.world.level.block.SoundType",
            name = "getVolume",
            type = Remap.Type.METHOD,
            remappedName = "a")
    public float getVolume() {
        return handle.a();
    }

    @Remap(classPath = "net.minecraft.world.level.block.SoundType",
            name = "getPitch",
            type = Remap.Type.METHOD,
            remappedName = "b")
    public float getPitch() {
        return handle.b();
    }

}
