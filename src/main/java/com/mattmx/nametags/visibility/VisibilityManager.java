package com.mattmx.nametags.visibility;

import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.preferences.PlayerPreferences;
import com.mattmx.nametags.preferences.PreferencesManager;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
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
     * Determines if a viewer should see a target player's nametag.
     * Checks if the target is invisible based on their current state.
     */
    public boolean shouldShowNametag(@NotNull Player viewer, @NotNull Player target) {
        boolean isInvisible = target.isInvisible() ||
            (target instanceof LivingEntity && ((LivingEntity) target).hasPotionEffect(PotionEffectType.INVISIBILITY));
        return shouldShowNametag(viewer, target, isInvisible);
    }

    /**
     * Determines if a viewer should see a target player's nametag, with explicit invisibility state.
     */
    public boolean shouldShowNametag(@NotNull Player viewer, @NotNull Player target, boolean targetIsInvisible) {
        // Check invisibility first
        if (targetIsInvisible) {
            // Spectators can see invisible entities
            if (viewer.getGameMode() == GameMode.SPECTATOR) {
                // fall through to other checks? Spectator usually sees everything translucent.
                // But we still respect hide-others preference? Usually spectators override.
                // Let's assume spectator sees it unless strictly hidden.
            } else {
                // Check teams
                Scoreboard scoreboard = viewer.getScoreboard();
                Team team = scoreboard.getEntryTeam(target.getName());

                // If on the same team and can see friendly invisibles
                if (team != null && team.hasEntry(viewer.getName())) {
                    if (!team.canSeeFriendlyInvisibles()) {
                        return false;
                    }
                    // If true, they can see, so we proceed to preference checks
                } else {
                    // Not on same team (or no team), invisible target is hidden
                    return false;
                }
            }
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
        // We use the default check which reads current invisibility state
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (!onlinePlayer.equals(target)) {
                // Logic is handled in refreshVisibilityBetween
                refreshVisibilityBetween(onlinePlayer, target);
            }
        }
    }

    /**
     * Updates what a viewer can see (when they toggle hide-others)
     */
    private void updateViewerVisibility(@NotNull Player viewer) {
        for (NameTagEntity nameTag : plugin.getEntityManager().getAllEntities()) {
            Player target = (Player) nameTag.getBukkitEntity();
            if (target.equals(viewer)) continue; // Skip own nametag
            
            refreshVisibilityBetween(viewer, target);
        }
    }

    /**
     * Called when a player enters view range of another player
     * This ensures visibility preferences are respected when players come into range
     */
    public void handlePlayerEnterRange(@NotNull Player viewer, @NotNull Player target) {
        refreshVisibilityBetween(viewer, target);
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
                    refreshVisibilityBetween(player, onlinePlayer);
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
        boolean isInvisible = target.isInvisible() ||
            (target instanceof LivingEntity && ((LivingEntity) target).hasPotionEffect(PotionEffectType.INVISIBILITY));
        refreshVisibilityBetween(viewer, target, isInvisible);
    }

    /**
     * Forces a refresh of visibility between two specific players with explicit invisibility state
     */
    public void refreshVisibilityBetween(@NotNull Player viewer, @NotNull Player target, boolean targetIsInvisible) {
        NameTagEntity targetTag = plugin.getEntityManager().getNameTagEntity(target);
        if (targetTag == null) return;

        boolean shouldSee = shouldShowNametag(viewer, target, targetIsInvisible);
        boolean currentlySees = targetTag.getPassenger().getViewers().contains(viewer.getUniqueId());

        if (shouldSee && !currentlySees && isInRange(viewer, target)) {
            targetTag.getPassenger().addViewer(viewer.getUniqueId());
            targetTag.sendPassengerPacket(viewer);
        } else if (!shouldSee && currentlySees) {
            targetTag.getPassenger().removeViewer(viewer.getUniqueId());
        }
    }
}
