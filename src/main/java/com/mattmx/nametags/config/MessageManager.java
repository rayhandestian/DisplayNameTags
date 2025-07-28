package com.mattmx.nametags.config;

import com.mattmx.nametags.NameTags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages messages from the configuration file
 */
public class MessageManager {
    private final @NotNull NameTags plugin;
    private final @NotNull MiniMessage miniMessage;
    private final @NotNull Map<String, String> defaultMessages;

    public MessageManager(@NotNull NameTags plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.defaultMessages = createDefaultMessages();
        ensureDefaultMessages();
    }

    /**
     * Creates the default message map
     */
    private Map<String, String> createDefaultMessages() {
        Map<String, String> defaults = new HashMap<>();
        
        // Toggle others messages
        defaults.put("toggle-others.enabled", "<green>You can now see other players' nametags</green>");
        defaults.put("toggle-others.disabled", "<red>You have hidden other players' nametags</red>");
        
        // Preview messages
        defaults.put("preview.enabled", "<green>You can now see your own nametag</green>");
        defaults.put("preview.disabled", "<red>You have hidden your own nametag preview</red>");
        
        // Hide self messages
        defaults.put("hide-self.enabled", "<green>Your nametag is now hidden from other players</green>");
        defaults.put("hide-self.disabled", "<green>Your nametag is now visible to other players</green>");
        
        // Admin messages
        defaults.put("admin.target-toggle-others", "<green>Toggled nametag visibility for <yellow>%player%</yellow></green>");
        defaults.put("admin.target-preview", "<green>Toggled nametag preview for <yellow>%player%</yellow></green>");
        defaults.put("admin.target-hide-self", "<green>Toggled nametag hiding for <yellow>%player%</yellow></green>");
        defaults.put("admin.target-enabled-others", "<yellow>An admin has enabled other players' nametags for you</yellow>");
        defaults.put("admin.target-disabled-others", "<yellow>An admin has hidden other players' nametags for you</yellow>");
        defaults.put("admin.target-enabled-preview", "<yellow>An admin has enabled nametag preview for you</yellow>");
        defaults.put("admin.target-disabled-preview", "<yellow>An admin has disabled nametag preview for you</yellow>");
        defaults.put("admin.target-enabled-hide", "<yellow>An admin has hidden your nametag from other players</yellow>");
        defaults.put("admin.target-disabled-hide", "<yellow>An admin has made your nametag visible to other players</yellow>");
        
        // Error messages
        defaults.put("errors.no-permission", "<red>You don't have permission to use this command</red>");
        defaults.put("errors.player-not-found", "<red>Player not found</red>");
        defaults.put("errors.invalid-subcommand", "<red>Invalid subcommand. Use: toggle-others, preview, hide-self, reload, debug</red>");
        defaults.put("errors.console-only", "<red>This command can only be used by players</red>");
        
        return defaults;
    }

    /**
     * Ensures all default messages exist in the config
     */
    private void ensureDefaultMessages() {
        boolean configChanged = false;
        
        for (Map.Entry<String, String> entry : defaultMessages.entrySet()) {
            String path = "messages." + entry.getKey();
            if (!plugin.getConfig().contains(path)) {
                plugin.getConfig().set(path, entry.getValue());
                configChanged = true;
            }
        }
        
        if (configChanged) {
            plugin.saveConfig();
            plugin.getLogger().info("Added missing default messages to config.yml");
        }
    }

    /**
     * Gets a message from the config and formats it as a Component
     */
    public @NotNull Component getMessage(@NotNull String path) {
        String configPath = "messages." + path;
        String message = plugin.getConfig().getString(configPath);
        
        // If message doesn't exist in config, use default or create one
        if (message == null) {
            message = defaultMessages.get(path);
            if (message == null) {
                message = "<red>Message not found: " + path + "</red>";
                plugin.getLogger().warning("No default message found for path: " + path);
            } else {
                // Add the default message to config for future reference
                plugin.getConfig().set(configPath, message);
                plugin.saveConfig();
                plugin.getLogger().info("Added missing message to config: " + path);
            }
        }
        
        return miniMessage.deserialize(message);
    }

    /**
     * Gets a message from the config with placeholder replacement
     */
    public @NotNull Component getMessage(@NotNull String path, @NotNull String placeholder, @NotNull String value) {
        String configPath = "messages." + path;
        String message = plugin.getConfig().getString(configPath);
        
        // If message doesn't exist in config, use default or create one
        if (message == null) {
            message = defaultMessages.get(path);
            if (message == null) {
                message = "<red>Message not found: " + path + "</red>";
                plugin.getLogger().warning("No default message found for path: " + path);
            } else {
                // Add the default message to config for future reference
                plugin.getConfig().set(configPath, message);
                plugin.saveConfig();
                plugin.getLogger().info("Added missing message to config: " + path);
            }
        }
        
        message = message.replace("%" + placeholder + "%", value);
        return miniMessage.deserialize(message);
    }

    /**
     * Gets toggle-others messages
     */
    public @NotNull Component getToggleOthersMessage(boolean enabled) {
        return getMessage(enabled ? "toggle-others.enabled" : "toggle-others.disabled");
    }

    /**
     * Gets preview messages
     */
    public @NotNull Component getPreviewMessage(boolean enabled) {
        return getMessage(enabled ? "preview.enabled" : "preview.disabled");
    }

    /**
     * Gets hide-self messages
     */
    public @NotNull Component getHideSelfMessage(boolean enabled) {
        return getMessage(enabled ? "hide-self.enabled" : "hide-self.disabled");
    }

    /**
     * Gets admin target messages
     */
    public @NotNull Component getAdminTargetMessage(@NotNull String action, @NotNull String playerName) {
        return getMessage("admin.target-" + action, "player", playerName);
    }

    /**
     * Gets admin notification messages for targets
     */
    public @NotNull Component getAdminNotificationMessage(@NotNull String action, boolean enabled) {
        String suffix = enabled ? "enabled" : "disabled";
        return getMessage("admin.target-" + suffix + "-" + action);
    }

    /**
     * Gets error messages
     */
    public @NotNull Component getErrorMessage(@NotNull String error) {
        return getMessage("errors." + error);
    }
}