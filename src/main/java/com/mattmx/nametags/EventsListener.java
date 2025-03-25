package com.mattmx.nametags;

import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.entity.trait.SneakTrait;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public class EventsListener implements Listener {

    private final @NotNull NameTags plugin;

    public EventsListener(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Initial creation of the player's nametag entity
        NameTagEntity nameTagEntity = plugin.getEntityManager()
            .getOrCreateNameTagEntity(player);
        nameTagEntity.updateVisibility();
        
        // Delay the nametag handling to ensure all entities are properly initialized
        new BukkitRunnable() {
            @Override
            public void run() {
                // First handle the joining player's visibility settings
                if (plugin.getToggleCommand().isNameTagsHidden(player)) {
                    // Remove them as a viewer from all nametag entities
                    for (NameTagEntity entity : plugin.getEntityManager().getAllEntities()) {
                        if (!entity.getBukkitEntity().getUniqueId().equals(player.getUniqueId())) {
                            entity.getPassenger().removeViewer(player.getUniqueId());
                            entity.updateVisibility();
                            entity.sendPassengerPacket(player);
                        }
                    }
                }
                
                // Then handle other players' visibility of the new player
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                        if (plugin.getToggleCommand().isNameTagsHidden(onlinePlayer)) {
                            nameTagEntity.getPassenger().removeViewer(onlinePlayer.getUniqueId());
                        } else {
                            nameTagEntity.getPassenger().addViewer(onlinePlayer.getUniqueId());
                        }
                        nameTagEntity.updateVisibility();
                        nameTagEntity.sendPassengerPacket(onlinePlayer);
                    }
                }
            }
        }.runTaskLater(plugin, 10L); // 10 tick delay (0.5 seconds)
        
        // Run another check after a longer delay to ensure everything is set correctly
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getToggleCommand().isNameTagsHidden(player)) {
                    for (NameTagEntity entity : plugin.getEntityManager().getAllEntities()) {
                        if (!entity.getBukkitEntity().getUniqueId().equals(player.getUniqueId())) {
                            entity.getPassenger().removeViewer(player.getUniqueId());
                            entity.updateVisibility();
                            entity.sendPassengerPacket(player);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 20L); // 20 tick delay (1 second)
    }

//    @EventHandler
//    public void onEntityRemove(@NotNull EntityRemoveFromWorldEvent event) {
//        plugin.getEntityManager().removeLastSentPassengersCache(event.getEntity().getEntityId());
//
//        NameTagEntity entity = plugin.getEntityManager()
//            .removeEntity(event.getEntity());
//
//        if (entity != null) {
//            entity.destroy();
//        }
//    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        plugin.getEntityManager().removeLastSentPassengersCache(event.getPlayer().getEntityId());

        // Remove as a viewer from all entities
        for (final NameTagEntity entity : plugin.getEntityManager().getAllEntities()) {
            entity.getPassenger().removeViewer(event.getPlayer().getUniqueId());
        }

        NameTagEntity entity = plugin.getEntityManager()
            .removeEntity(event.getPlayer());

        if (entity != null) {
            entity.destroy();
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        NameTagEntity nameTagEntity = plugin.getEntityManager()
            .getNameTagEntity(player);

        if (nameTagEntity == null) return;

        nameTagEntity.updateLocation();

        // Only show self nametag if enabled in config and player hasn't disabled nametags
        if (plugin.getConfig().getBoolean("show-self", false) && !plugin.getToggleCommand().isNameTagsHidden(player)) {
            nameTagEntity.getPassenger().removeViewer(nameTagEntity.getBukkitEntity().getUniqueId());
            nameTagEntity.getPassenger().addViewer(nameTagEntity.getBukkitEntity().getUniqueId());
            nameTagEntity.sendPassengerPacket(player);
        }
    }


    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        NameTagEntity nameTagEntity = plugin.getEntityManager()
                .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null) return;

        if (plugin.getConfig().getBoolean("show-self", false)) {
            // Hides/removes tag on death/respawn screen
            nameTagEntity.getPassenger().removeViewer(nameTagEntity.getBukkitEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        NameTagEntity nameTagEntity = plugin.getEntityManager()
                .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null) return;

        if (plugin.getConfig().getBoolean("show-self", false)) {

            String respawnWorld = event.getRespawnLocation().getWorld().getName();
            String playerWorld = event.getPlayer().getWorld().getName();
            // Ignoring since same action is handled at EventListener#onPlayerChangeWorld if player was killed in another world.
            if (!playerWorld.equalsIgnoreCase(respawnWorld)) return;

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Update entity location.
                nameTagEntity.updateLocation();
                // Add player back as viewer
                nameTagEntity.getPassenger().addViewer(nameTagEntity.getBukkitEntity().getUniqueId());
                // Send passenger packet
                nameTagEntity.sendPassengerPacket(event.getPlayer());
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerSneak(@NotNull PlayerToggleSneakEvent event) {
        if (!plugin.getConfig().getBoolean("sneak.enabled")) {
            return;
        }

        if (event.getPlayer().isInsideVehicle()) return;

        NameTagEntity nameTagEntity = plugin.getEntityManager()
            .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null) return;

        nameTagEntity.getTraits()
            .getOrAddTrait(SneakTrait.class, SneakTrait::new)
            .updateSneak(event.isSneaking());
    }
}
