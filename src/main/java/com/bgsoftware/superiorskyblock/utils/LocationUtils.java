package com.bgsoftware.superiorskyblock.utils;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.utils.chunks.ChunksTracker;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class LocationUtils {

    public static boolean isSameBlock(Location loc1, Location loc2){
        return loc1.getWorld().getName().equals(loc2.getWorld().getName()) && loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() && loc1.getBlockZ() == loc2.getBlockZ();
    }

    public static Location getLocation(String location){
        if(location == null || location.isEmpty())
            return null;

        String[] sections = location.split(",");

        World world = Bukkit.getWorld(sections[0]);
        double x = Double.parseDouble(sections[1]);
        double y = Double.parseDouble(sections[2]);
        double z = Double.parseDouble(sections[3]);
        float yaw = sections.length > 5 ? Float.parseFloat(sections[4]) : 0;
        float pitch = sections.length > 4 ? Float.parseFloat(sections[5]) : 0;

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static String getLocation(Location location){
        return location == null ? "" : location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ() + "," + location.getYaw() + "," + location.getPitch();
    }

    public static boolean isSafeBlock(Block block){
        Block underBlock = block.getRelative(BlockFace.DOWN), upperBlock = block.getRelative(BlockFace.UP);
        return upperBlock.getType() == Material.AIR && block.getType() == Material.AIR &&
                (underBlock.getType().isSolid() || underBlock.getRelative(BlockFace.DOWN).getType().isSolid());
    }

    public static boolean isChunkEmpty(Island island, ChunkSnapshot chunkSnapshot){
        for(int i = 0; i < 16; i++){
            if(!chunkSnapshot.isSectionEmpty(i)){
                return false;
            }
        }

        ChunksTracker.markEmpty(island, chunkSnapshot, true);

        return true;
    }

    public static Location getRelative(Location location, BlockFace face){
        return location.clone().add(face.getModX(), face.getModY(), face.getModZ());
    }

}
