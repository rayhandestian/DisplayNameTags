package com.mattmx.nametags.visibility;

import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.preferences.PlayerPreferences;
import com.mattmx.nametags.preferences.PreferencesManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Centralized visibility management for nametags
 */
public class VisibilityManager {
    private final @NotNull NameTags plugin;
    private final @NotNull PreferencesManager preferencesManager;

    public VisibilityManager(@NotNull NameTags plugin, @NotNull PreferencesManager preferencesManager) {
        this.plugin = plugin;
        this.preferencesManager = preferencesManager;
    }

    /**
     * Determines if a viewer should see a target player's nametag
     */
    public boolean shouldShowNametag(@NotNull Player viewer, @NotNull Player target) {
        // If Bukkit hides the target from the viewer (e.g. vanished player)
        if (!viewer.canSee(target)) {
            return false;
        }

        // If viewer has hidden others' nametags, they shouldn't see any
        PlayerPreferences viewerPrefs = preferencesManager.getPreferences(viewer);
        if (viewerPrefs.isHideOthersNametags()) {
            return false;
        }

        // If target has hidden their nametag from others
        PlayerPreferences targetPrefs = preferencesManager.getPreferences(target);
        if (targetPrefs.isHideFromOthers()) {
            // Check if viewer has admin permission to see hidden nametags
            return viewer.hasPermission("nametags.admin.see-hidden");
        }

        return true;
    }

    /**
     * Determines if a player should see their own nametag
     */
    public boolean shouldShowOwnNametag(@NotNull Player player) {
        PlayerPreferences preferences = preferencesManager.getPreferences(player);
        
        // Player preference overrides global config
        if (preferences.isShowOwnNametag()) {
            return true;
        }
        
        // Fall back to global config if player hasn't enabled preview
        return plugin.getConfig().getBoolean("show-self", false);
    }

    /**
     * Updates visibility for all players when a player's preferences change
     */
    public void updateVisibilityForPlayer(@NotNull Player player) {
        NameTagEntity playerTag = plugin.getEntityManager().getNameTagEntity(player);
        if (playerTag == null) return;

        // Update who can see this player's nametag
        updateViewersForTarget(player, playerTag);
        
        // Update what this player can see
        updateViewerVisibility(player);
        
        // Update self-visibility
        updateSelfVisibility(player, playerTag);
    }

    /**
     * Updates who can see a target player's nametag
     */
    private void updateViewersForTarget(@NotNull Player target, @NotNull NameTagEntity targetTag) {
        PlayerPreferences targetPrefs = preferencesManager.getPreferences(target);
        
        // If target is hiding from others, remove all viewers except admins
        if (targetPrefs.isHideFromOthers()) {
            for (UUID viewerUuid : targetTag.getPassenger().getViewers().toArray(new UUID[0])) {
                Player viewer = plugin.getServer().getPlayer(viewerUuid);
                if (viewer != null && !viewer.equals(target)) {
                    if (!viewer.hasPermission("nametags.admin.see-hidden")) {
                        targetTag.getPassenger().removeViewer(viewerUuid);
                    }
                }
            }
        } else {
            // Target is not hiding, add back viewers who should see them
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (!onlinePlayer.equals(target) && shouldShowNametag(onlinePlayer, target)) {
                    // Check if they're in range and should see the nametag
                    if (isInRange(onlinePlayer, target)) {
                        targetTag.getPassenger().addViewer(onlinePlayer.getUniqueId());
                        targetTag.sendPassengerPacket(onlinePlayer);
                    }
                }
            }
        }
    }

    /**
     * Updates what a viewer can see (when they toggle hide-others)
     */
    private void updateViewerVisibility(@NotNull Player viewer) {
        PlayerPreferences viewerPrefs = preferencesManager.getPreferences(viewer);
        
        for (NameTagEntity nameTag : plugin.getEntityManager().getAllEntities()) {
            Player target = (Player) nameTag.getBukkitEntity();
            if (target.equals(viewer)) continue; // Skip own nametag
            
            boolean shouldSee = shouldShowNametag(viewer, target);
            boolean currentlySees = nameTag.getPassenger().getViewers().contains(viewer.getUniqueId());
            
            if (shouldSee && !currentlySees && isInRange(viewer, target)) {
                nameTag.getPassenger().addViewer(viewer.getUniqueId());
                nameTag.sendPassengerPacket(viewer);
            } else if (!shouldSee && currentlySees) {
                nameTag.getPassenger().removeViewer(viewer.getUniqueId());
            }
        }
    }

    /**
     * Called when a player enters view range of another player
     * This ensures visibility preferences are respected when players come into range
     */
    public void handlePlayerEnterRange(@NotNull Player viewer, @NotNull Player target) {
        NameTagEntity targetTag = plugin.getEntityManager().getNameTagEntity(target);
        if (targetTag == null) return;

        boolean shouldSee = shouldShowNametag(viewer, target);
        boolean currentlySees = targetTag.getPassenger().getViewers().contains(viewer.getUniqueId());

        if (shouldSee && !currentlySees) {
            targetTag.getPassenger().addViewer(viewer.getUniqueId());
            targetTag.sendPassengerPacket(viewer);
        } else if (!shouldSee && currentlySees) {
            targetTag.getPassenger().removeViewer(viewer.getUniqueId());
        }
    }

    /**
     * Called when a player leaves view range of another player
     */
    public void handlePlayerLeaveRange(@NotNull Player viewer, @NotNull Player target) {
        NameTagEntity targetTag = plugin.getEntityManager().getNameTagEntity(target);
        if (targetTag == null) return;

        targetTag.getPassenger().removeViewer(viewer.getUniqueId());
    }

    /**
     * Updates self-visibility for a player
     */
    private void updateSelfVisibility(@NotNull Player player, @NotNull NameTagEntity playerTag) {
        boolean shouldShow = shouldShowOwnNametag(player);
        boolean currentlyShows = playerTag.getPassenger().getViewers().contains(player.getUniqueId());
        
        if (shouldShow && !currentlyShows) {
            playerTag.getPassenger().addViewer(player.getUniqueId());
            playerTag.sendPassengerPacket(player);
        } else if (!shouldShow && currentlyShows) {
            playerTag.getPassenger().removeViewer(player.getUniqueId());
        }
    }

    /**
     * Applies initial visibility settings when a player joins
     */
    public void applyInitialVisibility(@NotNull Player player) {
        NameTagEntity playerTag = plugin.getEntityManager().getNameTagEntity(player);
        if (playerTag == null) return;

        // Set up self-visibility
        updateSelfVisibility(player, playerTag);
        
        // Set up visibility with other players
        updateVisibilityForPlayer(player);
        
        // Update other players' visibility of this player
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                NameTagEntity otherTag = plugin.getEntityManager().getNameTagEntity(onlinePlayer);
                if (otherTag != null) {
                    boolean shouldSee = shouldShowNametag(player, onlinePlayer);
                    if (shouldSee && isInRange(player, onlinePlayer)) {
                        otherTag.getPassenger().addViewer(player.getUniqueId());
                        otherTag.sendPassengerPacket(player);
                    }
                }
            }
        }
    }

    /**
     * Checks if two players are in range to see each other's nametags
     */
    private boolean isInRange(@NotNull Player viewer, @NotNull Player target) {
        if (!viewer.getWorld().equals(target.getWorld())) {
            return false;
        }
        
        // Use the nametag's view range from the target's nametag
        NameTagEntity targetTag = plugin.getEntityManager().getNameTagEntity(target);
        if (targetTag == null) return false;
        
        float viewRange = targetTag.getMeta().getViewRange();
        if (viewRange <= 0) return false; // Invisible or no range
        
        double distance = viewer.getLocation().distance(target.getLocation());
        return distance <= viewRange;
    }

    /**
     * Called when a player quits to clean up visibility
     */
    public void handlePlayerQuit(@NotNull Player player) {
        // Remove from all other players' nametag viewers
        for (NameTagEntity nameTag : plugin.getEntityManager().getAllEntities()) {
            nameTag.getPassenger().removeViewer(player.getUniqueId());
        }
    }

    /**
     * Forces a complete refresh of visibility for all players
     * Useful for debugging or ensuring consistency
     */
    public void refreshAllVisibility() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateVisibilityForPlayer(player);
        }
    }

    /**
     * Forces a refresh of visibility between two specific players
     */
    public void refreshVisibilityBetween(@NotNull Player viewer, @NotNull Player target) {
        NameTagEntity targetTag = plugin.getEntityManager().getNameTagEntity(target);
        if (targetTag == null) return;

        boolean shouldSee = shouldShowNametag(viewer, target);
        boolean currentlySees = targetTag.getPassenger().getViewers().contains(viewer.getUniqueId());

        if (shouldSee && !currentlySees && isInRange(viewer, target)) {
            targetTag.getPassenger().addViewer(viewer.getUniqueId());
            targetTag.sendPassengerPacket(viewer);
        } else if (!shouldSee && currentlySees) {
            targetTag.getPassenger().removeViewer(viewer.getUniqueId());
        }
    }
}