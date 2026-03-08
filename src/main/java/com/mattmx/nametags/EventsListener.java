package com.mattmx.nametags;

import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.entity.trait.SneakTrait;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.UUID;

public class EventsListener implements Listener {

    private final @NotNull NameTags plugin;

    public EventsListener(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
            if (!event.getPlayer().isConnected()) {
                return;
            }

            plugin.getEntityManager()
                .getOrCreateNameTagEntity(event.getPlayer())
                .updateVisibility();
            
            // Apply initial visibility preferences
            plugin.getVisibilityManager().applyInitialVisibility(event.getPlayer());
        });
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        plugin.getEntityManager().removeLastSentPassengersCache(event.getPlayer().getEntityId());
        
        // Handle visibility cleanup
        plugin.getVisibilityManager().handlePlayerQuit(event.getPlayer());
        
        // Remove player preferences from cache
        plugin.getPreferencesManager().removeFromCache(event.getPlayer().getUniqueId());

        NameTagEntity entity = plugin.getEntityManager().removeEntity(event.getPlayer());

        if (entity != null) {
            entity.destroy();
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(@NotNull PlayerChangedWorldEvent event) {
        NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null) return;

        nameTagEntity.updateLocation();

        // Update visibility based on preferences
        plugin.getVisibilityManager().updateVisibilityForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        
        NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null) return;

        nameTagEntity.updateLocation();

        // Update visibility after teleport to handle vanish/appear changes
        plugin.getVisibilityManager().updateVisibilityForPlayer(event.getPlayer());
    }


    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        NameTagEntity nameTagEntity = plugin.getEntityManager()
            .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null) return;

        // Handle self-visibility based on preferences
        if (plugin.getVisibilityManager().shouldShowOwnNametag(event.getPlayer())) {
            // Hides/removes tag on death/respawn screen
            nameTagEntity.getPassenger().removeViewer(nameTagEntity.getBukkitEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        NameTagEntity nameTagEntity = plugin.getEntityManager()
            .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null) return;

        // Handle self-visibility based on preferences
        if (plugin.getVisibilityManager().shouldShowOwnNametag(event.getPlayer())) {

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
