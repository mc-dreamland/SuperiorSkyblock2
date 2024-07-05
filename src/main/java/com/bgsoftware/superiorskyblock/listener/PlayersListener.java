package com.bgsoftware.superiorskyblock.listener;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.data.DatabaseBridge;
import com.bgsoftware.superiorskyblock.api.data.DatabaseFilter;
import com.bgsoftware.superiorskyblock.api.enums.HitActionResult;
import com.bgsoftware.superiorskyblock.api.events.IslandUncoopPlayerEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandChest;
import com.bgsoftware.superiorskyblock.api.player.PlayerStatus;
import com.bgsoftware.superiorskyblock.api.player.respawn.RespawnAction;
import com.bgsoftware.superiorskyblock.api.service.region.MoveResult;
import com.bgsoftware.superiorskyblock.api.service.region.RegionManagerService;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.LazyReference;
import com.bgsoftware.superiorskyblock.core.Materials;
import com.bgsoftware.superiorskyblock.core.database.DatabaseResult;
import com.bgsoftware.superiorskyblock.core.database.cache.DatabaseCache;
import com.bgsoftware.superiorskyblock.core.database.serialization.IslandsDeserializer;
import com.bgsoftware.superiorskyblock.core.database.serialization.PlayersDeserializer;
import com.bgsoftware.superiorskyblock.core.events.EventResult;
import com.bgsoftware.superiorskyblock.core.formatting.Formatters;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.core.serialization.Serializers;
import com.bgsoftware.superiorskyblock.core.threads.BukkitExecutor;
import com.bgsoftware.superiorskyblock.island.IslandUtils;
import com.bgsoftware.superiorskyblock.island.SIslandChest;
import com.bgsoftware.superiorskyblock.island.builder.IslandBuilderImpl;
import com.bgsoftware.superiorskyblock.island.notifications.IslandNotifications;
import com.bgsoftware.superiorskyblock.island.privilege.IslandPrivileges;
import com.bgsoftware.superiorskyblock.island.top.SortingTypes;
import com.bgsoftware.superiorskyblock.player.PlayerLocales;
import com.bgsoftware.superiorskyblock.player.SuperiorNPCPlayer;
import com.bgsoftware.superiorskyblock.player.chat.PlayerChat;
import com.bgsoftware.superiorskyblock.player.respawn.RespawnActions;
import com.bgsoftware.superiorskyblock.world.BukkitEntities;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.InventoryHolder;

import java.math.BigDecimal;
import java.util.*;

public class PlayersListener implements Listener {

    private final LazyReference<RegionManagerService> regionManagerService = new LazyReference<RegionManagerService>() {
        @Override
        protected RegionManagerService create() {
            return plugin.getServices().getService(RegionManagerService.class);
        }
    };
    private final SuperiorSkyblockPlugin plugin;

    public PlayersListener(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    /* PLAYER NOTIFIERS */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerLogin(PlayerLoginEvent e) {
        List<SuperiorPlayer> duplicatedPlayers = plugin.getPlayers().matchAllPlayers(superiorPlayer ->
                superiorPlayer.getName().equalsIgnoreCase(e.getPlayer().getName()) &&
                        !superiorPlayer.getUniqueId().equals(e.getPlayer().getUniqueId()));

        if (!duplicatedPlayers.isEmpty()) {
            Log.info("Changing UUID of " + e.getPlayer().getName() + " to " + e.getPlayer().getUniqueId());

            SuperiorPlayer playerWithNewUUID = plugin.getPlayers().getSuperiorPlayer(e.getPlayer().getUniqueId(), false);

            if (playerWithNewUUID != null) {
                // Even tho we have duplicates, there's already a record for the new player.
                // Therefore, we just want to delete the old records from DB and cache.
                Log.info("Detected a record for the new player uuid already - deleting old ones...");
                // Delete all records
                duplicatedPlayers.forEach(duplicatedPlayer -> {
                    plugin.getPlayers().replacePlayers(duplicatedPlayer, null);
                    plugin.getPlayers().getPlayersContainer().removePlayer(duplicatedPlayer);
                });
                // We make sure the new player is correctly set in all caches by removing it and adding it.
                plugin.getPlayers().getPlayersContainer().removePlayer(playerWithNewUUID);
                plugin.getPlayers().getPlayersContainer().addPlayer(playerWithNewUUID);
            } else {
                // We first want to remove all original players.
                duplicatedPlayers.forEach(plugin.getPlayers().getPlayersContainer()::removePlayer);

                // We now want to create the new player.
                SuperiorPlayer newPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer().getUniqueId(), true, false);

                // We now want to replace all existing players
                duplicatedPlayers.forEach(originalPlayer -> {
                    if (originalPlayer != newPlayer)
                        plugin.getPlayers().replacePlayers(originalPlayer, newPlayer);
                });
            }
        }
    }

    private static final UUID CONSOLE_UUID = new UUID(0, 0);
    private void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(player);
        if (superiorPlayer != null) {
            plugin.getPlayers().getPlayersContainer().removePlayer(superiorPlayer);
        }

        DatabaseCache<SuperiorPlayer.Builder> databaseCache = new DatabaseCache<>();
        DatabaseBridge playersLoader = SuperiorSkyblockPlugin.getPlugin().getFactory().createDatabaseBridge((SuperiorPlayer) null);

        DatabaseFilter playerFilter = DatabaseFilter.fromFilter("player", uuid.toString());

        PlayersDeserializer.deserializeMissions(playersLoader, playerFilter, databaseCache);
        PlayersDeserializer.deserializePlayerSettings(playersLoader, playerFilter, databaseCache);
        PlayersDeserializer.deserializePersistentDataContainer(playersLoader, playerFilter, databaseCache);

        playersLoader.loadObject("players",
                DatabaseFilter.fromFilter("uuid", uuid.toString()),
                resultSetRaw -> {
                    DatabaseResult databaseResult = new DatabaseResult(resultSetRaw);

                    Optional<UUID> puuid = databaseResult.getUUID("uuid");
                    if (!puuid.isPresent()) {
                        Log.warn("Cannot load player with null uuid, skipping...");
                        return;
                    }

                    if (puuid.get().equals(CONSOLE_UUID)) {
                        Log.warn("Cannot load player with uuid 0 (it is reserved to CONSOLE), skipping...");
                        return;
                    }

                    plugin.getPlayers().getPlayersContainer().addPlayer(databaseCache.computeIfAbsentInfo(puuid.get(), SuperiorPlayer::newBuilder)
                            .setUniqueId(puuid.get())
                            .setName(databaseResult.getString("last_used_name").orElse("null"))
                            .setDisbands(databaseResult.getInt("disbands").orElse(0))
                            .setTextureValue(databaseResult.getString("last_used_skin").orElse(""))
                            .setLastTimeUpdated(databaseResult.getLong("last_time_updated").orElse(System.currentTimeMillis() / 1000))
                            .build());
                });
        DatabaseBridge islandLoader = SuperiorSkyblockPlugin.getPlugin().getFactory().createDatabaseBridge((Island) null);
        islandLoader.loadObject("islands",
                DatabaseFilter.fromFilter("owner", uuid.toString()),
                resultSetRaw -> {
                    DatabaseResult databaseResult = new DatabaseResult(resultSetRaw);
                    Optional<UUID> puuid = databaseResult.getUUID("uuid");
                    if (!puuid.isPresent()) {
                        Log.warn("Cannot load island with null uuid, skipping...");
                        return;
                    }
                    loadIslandData(puuid.get());
                    Island islandByUUID = plugin.getGrid().getIslandsContainer().getIslandByUUID(puuid.get());
                }
        );
    }

    public void loadIslandData(UUID islandUUID) {
        DatabaseBridge islandsLoader = SuperiorSkyblockPlugin.getPlugin().getFactory().createDatabaseBridge((Island) null);
        DatabaseCache<Island.Builder> databaseCache = new DatabaseCache<>();

        DatabaseFilter islandFilter = DatabaseFilter.fromFilter("island", islandUUID.toString());

        IslandsDeserializer.deserializeIslandHomes(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeMembers(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeBanned(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializePlayerPermissions(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeRolePermissions(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeUpgrades(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeWarps(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeBlockLimits(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeRatings(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeMissions(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeIslandFlags(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeGenerators(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeVisitors(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeEntityLimits(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeEffects(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeIslandChest(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeRoleLimits(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeWarpCategories(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeIslandBank(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeVisitorHomes(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeIslandSettings(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializeBankTransactions(islandsLoader, islandFilter, databaseCache);
        IslandsDeserializer.deserializePersistentDataContainer(islandsLoader, islandFilter, databaseCache);

        islandsLoader.loadObject("islands", DatabaseFilter.fromFilter("uuid", islandUUID.toString()), resultSetRaw -> {
            DatabaseResult databaseResult = new DatabaseResult(resultSetRaw);

            Optional<UUID> uuid = databaseResult.getUUID("uuid");
            if (!uuid.isPresent()) {
                Log.warn("Cannot load island with invalid uuid, skipping...");
                return;
            }

            Optional<UUID> ownerUUID = databaseResult.getUUID("owner");
            if (!ownerUUID.isPresent()) {
                Log.warn("Cannot load island with invalid owner uuid, skipping...");
                return;
            }

            SuperiorPlayer owner = plugin.getPlayers().getSuperiorPlayer(ownerUUID.get(), false);
            if (owner == null) {
                Log.warn("Cannot load island with unrecognized owner uuid: " + ownerUUID.get() + ", skipping...");
                return;
            }

            Optional<String> c = databaseResult.getString("center");
            Optional<Location> center = c.map(Serializers.LOCATION_SERIALIZER::deserialize);
            if (!center.isPresent()) {
                Log.warn("Cannot load island with invalid center, skipping...");
                return;
            }
            Island.Builder builder = databaseCache.computeIfAbsentInfo(uuid.get(), IslandBuilderImpl::new)
                    .setOwner(owner).setUniqueId(uuid.get())
                    .setName(databaseResult.getString("name").orElse(""))
                    .setCenter(center.get())
                    .setSchematicName(databaseResult.getString("island_type").orElse(null))
                    .setCreationTime(databaseResult.getLong("creation_time").orElse(System.currentTimeMillis() / 1000L))
                    .setDiscord(databaseResult.getString("discord").orElse("None"))
                    .setPaypal(databaseResult.getString("paypal").orElse("None"))
                    .setBonusWorth(databaseResult.getBigDecimal("worth_bonus").orElse(BigDecimal.ZERO))
                    .setBonusLevel(databaseResult.getBigDecimal("levels_bonus").orElse(BigDecimal.ZERO))
                    .setLocked(databaseResult.getBoolean("locked").orElse(false))
                    .setIgnored(databaseResult.getBoolean("ignored").orElse(false))
                    .setDescription(databaseResult.getString("description").orElse(""))
                    .setGeneratedSchematics(databaseResult.getInt("generated_schematics").orElse(0))
                    .setUnlockedWorlds(databaseResult.getInt("unlocked_worlds").orElse(0))
                    .setLastTimeUpdated(databaseResult.getLong("last_time_updated").orElse(System.currentTimeMillis() / 1000L));

            databaseResult.getString("dirty_chunks").ifPresent(dirtyChunks -> {
                IslandsDeserializer.deserializeDirtyChunks(builder, dirtyChunks);
            });

            databaseResult.getString("block_counts").ifPresent(blockCounts -> {
                IslandsDeserializer.deserializeBlockCounts(builder, blockCounts);
            });

            databaseResult.getString("entity_counts").ifPresent(entityCounts -> {
                IslandsDeserializer.deserializeEntityCounts(builder, entityCounts);
            });

            plugin.getGrid().getIslandsContainer().addIsland(builder.build());

        });
    }

    @EventHandler(priority = EventPriority.HIGHEST  , ignoreCancelled = true)
    private void onPlayerJoinLoad(PlayerJoinEvent e) {
        loadPlayer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerJoin(PlayerJoinEvent e) {
        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        if (superiorPlayer instanceof SuperiorNPCPlayer)
            return;

        // Updating the name of the player.
        if (!superiorPlayer.getName().equals(e.getPlayer().getName())) {
            plugin.getEventsBus().callPlayerChangeNameEvent(superiorPlayer, e.getPlayer().getName());
            superiorPlayer.updateName();
        }

        // Handling player join
        if (superiorPlayer.isShownAsOnline())
            IslandNotifications.notifyPlayerJoin(superiorPlayer);

        Location playerLocation = e.getPlayer().getLocation();
        Island island = plugin.getGrid().getIslandAt(playerLocation);


        MoveResult moveResult = this.regionManagerService.get().handlePlayerJoin(superiorPlayer, playerLocation);
        boolean teleportToSpawn = moveResult != MoveResult.SUCCESS;

        BukkitExecutor.sync(() -> {
            if (!e.getPlayer().isOnline())
                return;

            // Updating skin of the player
            if (!plugin.getProviders().notifySkinsListeners(superiorPlayer))
                plugin.getNMSPlayers().setSkinTexture(superiorPlayer);

            if (!superiorPlayer.hasBypassModeEnabled()) {
                Island delayedIsland = plugin.getGrid().getIslandAt(e.getPlayer().getLocation());
                // Checking if the player is in the islands world, not inside an island.
                if ((delayedIsland == island && teleportToSpawn) ||
                        (plugin.getGrid().isIslandsWorld(superiorPlayer.getWorld()) && delayedIsland == null)) {
                    superiorPlayer.teleport(plugin.getGrid().getSpawnIsland());
                    if (!teleportToSpawn)
                        Message.ISLAND_GOT_DELETED_WHILE_INSIDE.send(superiorPlayer);
                }
            }

            // Checking auto language detection
            if (plugin.getSettings().isAutoLanguageDetection() && !e.getPlayer().hasPlayedBefore()) {
                Locale playerLocale = plugin.getNMSPlayers().getPlayerLocale(e.getPlayer());
                if (playerLocale != null && PlayerLocales.isValidLocale(playerLocale) &&
                        !superiorPlayer.getUserLocale().equals(playerLocale)) {
                    if (plugin.getEventsBus().callPlayerChangeLanguageEvent(superiorPlayer, playerLocale))
                        superiorPlayer.setUserLocale(playerLocale);
                }
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerQuit(PlayerQuitEvent e) {
        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        if (superiorPlayer instanceof SuperiorNPCPlayer)
            return;

        // Removing coop status from other islands.
        for (Island _island : plugin.getGrid().getIslands()) {
            if (_island.isCoop(superiorPlayer)) {
                if (plugin.getEventsBus().callIslandUncoopPlayerEvent(_island, null, superiorPlayer, IslandUncoopPlayerEvent.UncoopReason.SERVER_LEAVE)) {
                    _island.removeCoop(superiorPlayer);
                    IslandUtils.sendMessage(_island, Message.UNCOOP_LEFT_ANNOUNCEMENT, Collections.emptyList(), superiorPlayer.getName());
                }
            }
        }

        // Handling player quit
        if (superiorPlayer.isShownAsOnline())
            IslandNotifications.notifyPlayerQuit(superiorPlayer);

        // Remove coop players
        Island island = superiorPlayer.getIsland();
        if (island != null && plugin.getSettings().isAutoUncoopWhenAlone() && !island.getCoopPlayers().isEmpty()) {
            boolean shouldRemoveCoops = island.getIslandMembers(true).stream().noneMatch(islandMember ->
                    islandMember != superiorPlayer && island.hasPermission(islandMember, IslandPrivileges.UNCOOP_MEMBER) && islandMember.isOnline());

            if (shouldRemoveCoops) {
                for (SuperiorPlayer coopPlayer : island.getCoopPlayers()) {
                    island.removeCoop(coopPlayer);
                    Message.UNCOOP_AUTO_ANNOUNCEMENT.send(coopPlayer);
                }
            }
        }

        this.regionManagerService.get().handlePlayerQuit(superiorPlayer, e.getPlayer().getLocation());

        // Remove all player chat-listeners
        PlayerChat.remove(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerGameModeChange(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() == GameMode.SPECTATOR) {
            IslandNotifications.notifyPlayerQuit(plugin.getPlayers().getSuperiorPlayer(e.getPlayer()));
        } else if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            IslandNotifications.notifyPlayerJoin(plugin.getPlayers().getSuperiorPlayer(e.getPlayer()));
        }
    }

    /* PLAYER MOVES */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    private void onPlayerMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();

        boolean movedFullBlock = from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ() ||
                from.getBlockY() != to.getBlockY();

        if (!movedFullBlock)
            return;

        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        if (superiorPlayer instanceof SuperiorNPCPlayer || superiorPlayer.hasPlayerStatus(PlayerStatus.VOID_TELEPORT))
            return;

        MoveResult moveResult = this.regionManagerService.get().handlePlayerMove(superiorPlayer, from, to);
        switch (moveResult) {
            case VOID_TELEPORT:
            case SUCCESS:
                break;
            default:
                e.setCancelled(true);
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlayerTeleport(PlayerTeleportEvent e) {
        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        if (superiorPlayer == null || superiorPlayer instanceof SuperiorNPCPlayer)
            return;

        MoveResult moveResult = this.regionManagerService.get().handlePlayerTeleport(superiorPlayer, e.getFrom(), e.getTo());
        if (moveResult != MoveResult.SUCCESS)
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
        Island island = plugin.getGrid().getIslandAt(e.getPlayer().getLocation());
        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        if (island != null && superiorPlayer.hasIslandFlyEnabled() && !e.getPlayer().getAllowFlight() &&
                island.hasPermission(superiorPlayer, IslandPrivileges.FLY))
            BukkitExecutor.sync(() -> {
                e.getPlayer().setAllowFlight(true);
                e.getPlayer().setFlying(true);
            }, 1L);
    }

    /* PVP */

    @EventHandler(priority = EventPriority.NORMAL /* Set to NORMAL, so it doesn't conflict with vanish plugins */, ignoreCancelled = true)
    private void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player))
            return;

        SuperiorPlayer targetPlayer = plugin.getPlayers().getSuperiorPlayer((Player) e.getEntity());

        if (targetPlayer instanceof SuperiorNPCPlayer)
            return;

        Island island = plugin.getGrid().getIslandAt(e.getEntity().getLocation());

        SuperiorPlayer damagerPlayer = !(e instanceof EntityDamageByEntityEvent) ? null :
                BukkitEntities.getPlayerSource(((EntityDamageByEntityEvent) e).getDamager())
                        .map(plugin.getPlayers()::getSuperiorPlayer).orElse(null);

        // Some plugins, such as Sentinel, may actually cause a NPC to attack.
        if (damagerPlayer instanceof SuperiorNPCPlayer)
            return;

        if (damagerPlayer == null) {
            if (island != null) {
                if (island.isSpawn() ? (plugin.getSettings().getSpawn().isProtected() && !plugin.getSettings().getSpawn().isPlayersDamage()) :
                        ((!plugin.getSettings().isVisitorsDamage() && island.isVisitor(targetPlayer, false)) ||
                                (!plugin.getSettings().isCoopDamage() && island.isCoop(targetPlayer))))
                    e.setCancelled(true);
            }

            return;
        }

        boolean cancelFlames = false;
        boolean cancelEvent = false;
        Message messageToSend = null;

        HitActionResult hitActionResult = damagerPlayer.canHit(targetPlayer);

        switch (hitActionResult) {
            case ISLAND_TEAM_PVP:
                messageToSend = Message.HIT_ISLAND_MEMBER;
                break;
            case ISLAND_PVP_DISABLE:
            case TARGET_ISLAND_PVP_DISABLE:
                messageToSend = Message.HIT_PLAYER_IN_ISLAND;
                break;
        }

        if (hitActionResult != HitActionResult.SUCCESS) {
            cancelFlames = true;
            cancelEvent = true;
        }

        if (cancelEvent)
            e.setCancelled(true);

        if (messageToSend != null)
            messageToSend.send(damagerPlayer);

        Player target = targetPlayer.asPlayer();

        if (target != null && cancelFlames && ((EntityDamageByEntityEvent) e).getDamager() instanceof Arrow &&
                target.getFireTicks() > 0)
            target.setFireTicks(0);
    }

    /* CHAT */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onPlayerAsyncChatLowest(AsyncPlayerChatEvent e) {
        // PlayerChat should be on LOWEST priority so other chat plugins don't conflict.
        PlayerChat playerChat = PlayerChat.getChatListener(e.getPlayer());
        if (playerChat != null && playerChat.supply(e.getMessage())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    private void onPlayerAsyncChat(AsyncPlayerChatEvent e) {
        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer());
        Island island = superiorPlayer.getIsland();

        if (superiorPlayer.hasTeamChatEnabled()) {
            if (island == null) {
                if (!plugin.getEventsBus().callPlayerToggleTeamChatEvent(superiorPlayer))
                    return;

                superiorPlayer.toggleTeamChat();
                return;
            }

            e.setCancelled(true);

            EventResult<String> eventResult = plugin.getEventsBus().callIslandChatEvent(island, superiorPlayer,
                    superiorPlayer.hasPermissionWithoutOP("superior.chat.color") ?
                            Formatters.COLOR_FORMATTER.format(e.getMessage()) : e.getMessage());

            if (eventResult.isCancelled())
                return;

            IslandUtils.sendMessage(island, Message.TEAM_CHAT_FORMAT, Collections.emptyList(),
                    superiorPlayer.getPlayerRole(), superiorPlayer.getName(), eventResult.getResult());

            Message.SPY_TEAM_CHAT_FORMAT.send(Bukkit.getConsoleSender(), superiorPlayer.getPlayerRole().getDisplayName(),
                    superiorPlayer.getName(), eventResult.getResult());
            for (Player _onlinePlayer : Bukkit.getOnlinePlayers()) {
                SuperiorPlayer onlinePlayer = plugin.getPlayers().getSuperiorPlayer(_onlinePlayer);
                if (onlinePlayer.hasAdminSpyEnabled())
                    Message.SPY_TEAM_CHAT_FORMAT.send(onlinePlayer, superiorPlayer.getPlayerRole().getDisplayName(),
                            superiorPlayer.getName(), eventResult.getResult());
            }
        } else {
            String islandNameFormat = Message.NAME_CHAT_FORMAT.getMessage(PlayerLocales.getDefaultLocale(),
                    island == null ? "" : plugin.getSettings().getIslandNames().isColorSupport() ?
                            Formatters.COLOR_FORMATTER.format(island.getName()) : island.getName());

            e.setFormat(e.getFormat()
                    .replace("{island-level}", String.valueOf(island == null ? 0 : island.getIslandLevel()))
                    .replace("{island-level-format}", String.valueOf(island == null ? 0 :
                            Formatters.FANCY_NUMBER_FORMATTER.format(island.getIslandLevel(), superiorPlayer.getUserLocale())))
                    .replace("{island-worth}", String.valueOf(island == null ? 0 : island.getWorth()))
                    .replace("{island-worth-format}", String.valueOf(island == null ? 0 :
                            Formatters.FANCY_NUMBER_FORMATTER.format(island.getWorth(), superiorPlayer.getUserLocale())))
                    .replace("{island-name}", islandNameFormat == null ? "" : islandNameFormat)
                    .replace("{island-role}", superiorPlayer.getPlayerRole().getDisplayName())
                    .replace("{island-position-worth}", island == null ? "" : (plugin.getGrid().getIslandPosition(island, SortingTypes.BY_WORTH) + 1) + "")
                    .replace("{island-position-level}", island == null ? "" : (plugin.getGrid().getIslandPosition(island, SortingTypes.BY_LEVEL) + 1) + "")
                    .replace("{island-position-rating}", island == null ? "" : (plugin.getGrid().getIslandPosition(island, SortingTypes.BY_RATING) + 1) + "")
                    .replace("{island-position-players}", island == null ? "" : (plugin.getGrid().getIslandPosition(island, SortingTypes.BY_PLAYERS) + 1) + "")
            );
        }
    }

    /* SCHEMATICS */

    @EventHandler
    private void onSchematicSelection(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() != Materials.GOLDEN_AXE.toBukkitType() ||
                !(e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK))
            return;

        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        if (!superiorPlayer.hasSchematicModeEnabled())
            return;

        e.setCancelled(true);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR) {
            Message.SCHEMATIC_RIGHT_SELECT.send(superiorPlayer, Formatters.LOCATION_FORMATTER.format(e.getClickedBlock().getLocation()));
            superiorPlayer.setSchematicPos1(e.getClickedBlock());
        } else {
            Message.SCHEMATIC_LEFT_SELECT.send(superiorPlayer, Formatters.LOCATION_FORMATTER.format(e.getClickedBlock().getLocation()));
            superiorPlayer.setSchematicPos2(e.getClickedBlock());
        }

        if (superiorPlayer.getSchematicPos1() != null && superiorPlayer.getSchematicPos2() != null)
            Message.SCHEMATIC_READY_TO_CREATE.send(superiorPlayer);
    }

    /* ISLAND CHESTS */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onIslandChestInteract(InventoryClickEvent e) {
        InventoryHolder inventoryHolder = e.getView().getTopInventory() == null ? null : e.getView().getTopInventory().getHolder();

        if (!(inventoryHolder instanceof IslandChest))
            return;

        SIslandChest islandChest = (SIslandChest) inventoryHolder;

        if (islandChest.isUpdating()) {
            e.setCancelled(true);
        } else {
            islandChest.updateContents();
        }
    }

    /* VOID TELEPORT */

    @EventHandler
    private void onPlayerFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player))
            return;

        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(e.getEntity());
        if (superiorPlayer.hasPlayerStatus(PlayerStatus.VOID_TELEPORT)) {
            e.setCancelled(true);
        }

    }

    /* PLAYER DEATH */

    @EventHandler
    private void onPlayerRespawn(PlayerRespawnEvent event) {
        for (RespawnAction respawnAction : plugin.getSettings().getPlayerRespawn()) {
            if (respawnAction == RespawnActions.VANILLA || respawnAction.canPerform(event)) {
                respawnAction.perform(event);
                return;
            }
        }

    }

}
