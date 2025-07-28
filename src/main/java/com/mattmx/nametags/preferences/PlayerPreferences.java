package com.mattmx.nametags.preferences;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents a player's nametag preferences
 */
public class PlayerPreferences {
    private final @NotNull UUID playerUuid;
    private boolean hideOthersNametags;
    private boolean showOwnNametag;
    private boolean hideFromOthers;

    public PlayerPreferences(@NotNull UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.hideOthersNametags = false;
        this.showOwnNametag = false;
        this.hideFromOthers = false;
    }

    public PlayerPreferences(@NotNull UUID playerUuid, boolean hideOthersNametags, boolean showOwnNametag, boolean hideFromOthers) {
        this.playerUuid = playerUuid;
        this.hideOthersNametags = hideOthersNametags;
        this.showOwnNametag = showOwnNametag;
        this.hideFromOthers = hideFromOthers;
    }

    public @NotNull UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean isHideOthersNametags() {
        return hideOthersNametags;
    }

    public void setHideOthersNametags(boolean hideOthersNametags) {
        this.hideOthersNametags = hideOthersNametags;
    }

    public boolean isShowOwnNametag() {
        return showOwnNametag;
    }

    public void setShowOwnNametag(boolean showOwnNametag) {
        this.showOwnNametag = showOwnNametag;
    }

    public boolean isHideFromOthers() {
        return hideFromOthers;
    }

    public void setHideFromOthers(boolean hideFromOthers) {
        this.hideFromOthers = hideFromOthers;
    }

    /**
     * Creates a copy of these preferences
     */
    public PlayerPreferences copy() {
        return new PlayerPreferences(playerUuid, hideOthersNametags, showOwnNametag, hideFromOthers);
    }

    /**
     * Checks if all preferences are set to their default values (false)
     */
    public boolean isDefault() {
        return !hideOthersNametags && !showOwnNametag && !hideFromOthers;
    }

    /**
     * Checks if any preference is set to a non-default value (true)
     */
    public boolean hasNonDefaultSettings() {
        return hideOthersNametags || showOwnNametag || hideFromOthers;
    }

    @Override
    public String toString() {
        return "PlayerPreferences{" +
                "playerUuid=" + playerUuid +
                ", hideOthersNametags=" + hideOthersNametags +
                ", showOwnNametag=" + showOwnNametag +
                ", hideFromOthers=" + hideFromOthers +
                '}';
    }
}