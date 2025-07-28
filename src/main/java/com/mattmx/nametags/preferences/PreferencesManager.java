package com.mattmx.nametags.preferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mattmx.nametags.NameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player preferences with file-based persistence and in-memory caching
 */
public class PreferencesManager {
    private final @NotNull NameTags plugin;
    private final @NotNull File preferencesFile;
    private final @NotNull Gson gson;
    private final @NotNull ConcurrentHashMap<UUID, PlayerPreferences> cache;

    public PreferencesManager(@NotNull NameTags plugin) {
        this.plugin = plugin;
        this.preferencesFile = new File(plugin.getDataFolder(), "player-preferences.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = new ConcurrentHashMap<>();
        
        loadPreferences();
    }

    /**
     * Gets player preferences, creating default ones if they don't exist
     */
    public @NotNull PlayerPreferences getPreferences(@NotNull UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, PlayerPreferences::new);
    }

    /**
     * Gets player preferences for a player
     */
    public @NotNull PlayerPreferences getPreferences(@NotNull Player player) {
        return getPreferences(player.getUniqueId());
    }

    /**
     * Updates a specific preference for a player
     */
    public void updatePreference(@NotNull UUID playerUuid, @NotNull PreferenceType type, boolean value) {
        PlayerPreferences preferences = getPreferences(playerUuid);
        
        switch (type) {
            case HIDE_OTHERS_NAMETAGS -> preferences.setHideOthersNametags(value);
            case SHOW_OWN_NAMETAG -> preferences.setShowOwnNametag(value);
            case HIDE_FROM_OTHERS -> preferences.setHideFromOthers(value);
        }
        
        savePreferencesAsync();
    }

    /**
     * Updates a specific preference for a player
     */
    public void updatePreference(@NotNull Player player, @NotNull PreferenceType type, boolean value) {
        updatePreference(player.getUniqueId(), type, value);
    }

    /**
     * Toggles a specific preference for a player and returns the new value
     */
    public boolean togglePreference(@NotNull UUID playerUuid, @NotNull PreferenceType type) {
        PlayerPreferences preferences = getPreferences(playerUuid);
        boolean newValue;
        
        switch (type) {
            case HIDE_OTHERS_NAMETAGS -> {
                newValue = !preferences.isHideOthersNametags();
                preferences.setHideOthersNametags(newValue);
            }
            case SHOW_OWN_NAMETAG -> {
                newValue = !preferences.isShowOwnNametag();
                preferences.setShowOwnNametag(newValue);
            }
            case HIDE_FROM_OTHERS -> {
                newValue = !preferences.isHideFromOthers();
                preferences.setHideFromOthers(newValue);
            }
            default -> throw new IllegalArgumentException("Unknown preference type: " + type);
        }
        
        savePreferencesAsync();
        return newValue;
    }

    /**
     * Toggles a specific preference for a player and returns the new value
     */
    public boolean togglePreference(@NotNull Player player, @NotNull PreferenceType type) {
        return togglePreference(player.getUniqueId(), type);
    }

    /**
     * Removes a player's preferences from cache (called on quit)
     * If the player has default settings, they won't be saved to file
     */
    public void removeFromCache(@NotNull UUID playerUuid) {
        PlayerPreferences preferences = cache.get(playerUuid);
        if (preferences != null && preferences.isDefault()) {
            plugin.getLogger().fine("Player " + playerUuid + " had default preferences, not saving to file");
        }
        cache.remove(playerUuid);
    }

    /**
     * Gets the mapped entities for debugging purposes
     */
    public @NotNull java.util.Map<UUID, PlayerPreferences> getMappedEntities() {
        return new java.util.HashMap<>(cache);
    }

    /**
     * Loads preferences from file
     */
    private void loadPreferences() {
        if (!preferencesFile.exists()) {
            plugin.getLogger().info("Player preferences file not found, creating new one.");
            return;
        }

        try (FileReader reader = new FileReader(preferencesFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            
            for (String uuidString : root.keySet()) {
                try {
                    UUID playerUuid = UUID.fromString(uuidString);
                    JsonObject prefObj = root.getAsJsonObject(uuidString);
                    
                    boolean hideOthers = prefObj.has("hideOthersNametags") && prefObj.get("hideOthersNametags").getAsBoolean();
                    boolean showOwn = prefObj.has("showOwnNametag") && prefObj.get("showOwnNametag").getAsBoolean();
                    boolean hideFromOthers = prefObj.has("hideFromOthers") && prefObj.get("hideFromOthers").getAsBoolean();
                    
                    PlayerPreferences preferences = new PlayerPreferences(playerUuid, hideOthers, showOwn, hideFromOthers);
                    cache.put(playerUuid, preferences);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in preferences file: " + uuidString);
                }
            }
            
            plugin.getLogger().info("Loaded preferences for " + cache.size() + " players.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player preferences", e);
        }
    }

    /**
     * Saves preferences to file asynchronously
     */
    private void savePreferencesAsync() {
        plugin.getExecutor().execute(this::savePreferences);
    }

    /**
     * Saves preferences to file synchronously
     */
    public void savePreferences() {
        try {
            // Ensure data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            JsonObject root = new JsonObject();
            int savedCount = 0;
            int skippedCount = 0;
            
            for (PlayerPreferences preferences : cache.values()) {
                // Only save players with non-default preferences (optimization)
                if (preferences.hasNonDefaultSettings()) {
                    JsonObject prefObj = new JsonObject();
                    prefObj.addProperty("hideOthersNametags", preferences.isHideOthersNametags());
                    prefObj.addProperty("showOwnNametag", preferences.isShowOwnNametag());
                    prefObj.addProperty("hideFromOthers", preferences.isHideFromOthers());
                    
                    root.add(preferences.getPlayerUuid().toString(), prefObj);
                    savedCount++;
                } else {
                    skippedCount++;
                }
            }

            try (FileWriter writer = new FileWriter(preferencesFile)) {
                gson.toJson(root, writer);
            }
            
            plugin.getLogger().fine(String.format("Saved %d player preferences, skipped %d default entries",
                savedCount, skippedCount));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player preferences", e);
        }
    }

    /**
     * Enum for preference types
     */
    public enum PreferenceType {
        HIDE_OTHERS_NAMETAGS,
        SHOW_OWN_NAMETAG,
        HIDE_FROM_OTHERS
    }
}