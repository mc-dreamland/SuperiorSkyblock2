package com.bgsoftware.superiorskyblock.external;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.core.key.Keys;
import com.bgsoftware.superiorskyblock.module.BuiltinModules;
import com.bgsoftware.superiorskyblock.module.upgrades.type.UpgradeTypeEntityLimits;
import com.bgsoftware.superiorskyblock.world.BukkitEntities;
import dev.rosewood.rosestacker.event.EntityUnstackEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class RoseStackerHook {

    private static SuperiorSkyblockPlugin plugin;

    public static void register(SuperiorSkyblockPlugin plugin) {
        RoseStackerHook.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(new ListenerImpl(), plugin);
    }

    private static class ListenerImpl implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onEntityUnstack(EntityUnstackEvent event) {
            LivingEntity livingEntity = event.getStack().getEntity();

            if (!BuiltinModules.UPGRADES.isUpgradeTypeEnabled(UpgradeTypeEntityLimits.class) ||
                    !BukkitEntities.canHaveLimit(livingEntity.getType()))
                return;

            Island island = plugin.getGrid().getIslandAt(livingEntity.getLocation());

            if (island == null)
                return;

            // RoseStacker spawns an extra entity, we want to increase
            island.getEntitiesTracker().trackEntity(Keys.of(livingEntity), 1);
        }

    }

}
