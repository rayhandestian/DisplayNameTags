package com.mattmx.nametags.commands;

import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.config.MessageManager;
import com.mattmx.nametags.preferences.PreferencesManager;
import com.mattmx.nametags.visibility.VisibilityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced command handler with subcommands for nametag features
 */
public class NameTagsSubCommand implements CommandExecutor, TabCompleter {
    private final @NotNull NameTags plugin;
    private final @NotNull PreferencesManager preferencesManager;
    private final @NotNull VisibilityManager visibilityManager;
    private final @NotNull MessageManager messageManager;

    public NameTagsSubCommand(@NotNull NameTags plugin) {
        this.plugin = plugin;
        this.preferencesManager = plugin.getPreferencesManager();
        this.visibilityManager = plugin.getVisibilityManager();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "toggle-others" -> handleToggleOthers(sender, args);
            case "preview" -> handlePreview(sender, args);
            case "hide-self" -> handleHideSelf(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender);
            case "refresh" -> handleRefresh(sender, args);
            default -> {
                sender.sendMessage(messageManager.getErrorMessage("invalid-subcommand"));
                return true;
            }
        }
        
        return true;
    }

    private void handleToggleOthers(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            // Self command
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messageManager.getErrorMessage("console-only"));
                return;
            }
            
            if (!player.hasPermission("nametags.command.toggle-others")) {
                sender.sendMessage(messageManager.getErrorMessage("no-permission"));
                return;
            }
            
            boolean newValue = preferencesManager.togglePreference(player, PreferencesManager.PreferenceType.HIDE_OTHERS_NAMETAGS);
            visibilityManager.updateVisibilityForPlayer(player);
            
            player.sendMessage(messageManager.getToggleOthersMessage(!newValue));
        } else if (args.length >= 2) {
            // Admin command for other player
            if (!sender.hasPermission("nametags.command.admin.toggle-others")) {
                sender.sendMessage(messageManager.getErrorMessage("no-permission"));
                return;
            }
            
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.getErrorMessage("player-not-found"));
                return;
            }
            
            // Check for -notify flag
            boolean notify = args.length >= 3 && args[2].equalsIgnoreCase("-notify");
            
            boolean newValue = preferencesManager.togglePreference(target, PreferencesManager.PreferenceType.HIDE_OTHERS_NAMETAGS);
            visibilityManager.updateVisibilityForPlayer(target);
            
            sender.sendMessage(messageManager.getAdminTargetMessage("toggle-others", target.getName()));
            
            // Only notify target if -notify flag is present
            if (notify) {
                target.sendMessage(messageManager.getAdminNotificationMessage("others", !newValue));
            }
        }
    }

    private void handlePreview(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            // Self command
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messageManager.getErrorMessage("console-only"));
                return;
            }
            
            if (!player.hasPermission("nametags.command.preview")) {
                sender.sendMessage(messageManager.getErrorMessage("no-permission"));
                return;
            }
            
            boolean newValue = preferencesManager.togglePreference(player, PreferencesManager.PreferenceType.SHOW_OWN_NAMETAG);
            visibilityManager.updateVisibilityForPlayer(player);
            
            player.sendMessage(messageManager.getPreviewMessage(newValue));
        } else if (args.length >= 2) {
            // Admin command for other player
            if (!sender.hasPermission("nametags.command.admin.preview")) {
                sender.sendMessage(messageManager.getErrorMessage("no-permission"));
                return;
            }
            
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.getErrorMessage("player-not-found"));
                return;
            }
            
            // Check for -notify flag
            boolean notify = args.length >= 3 && args[2].equalsIgnoreCase("-notify");
            
            boolean newValue = preferencesManager.togglePreference(target, PreferencesManager.PreferenceType.SHOW_OWN_NAMETAG);
            visibilityManager.updateVisibilityForPlayer(target);
            
            sender.sendMessage(messageManager.getAdminTargetMessage("preview", target.getName()));
            
            // Only notify target if -notify flag is present
            if (notify) {
                target.sendMessage(messageManager.getAdminNotificationMessage("preview", newValue));
            }
        }
    }

    private void handleHideSelf(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            // Self command
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messageManager.getErrorMessage("console-only"));
                return;
            }
            
            if (!player.hasPermission("nametags.command.hide-self")) {
                sender.sendMessage(messageManager.getErrorMessage("no-permission"));
                return;
            }
            
            boolean newValue = preferencesManager.togglePreference(player, PreferencesManager.PreferenceType.HIDE_FROM_OTHERS);
            visibilityManager.updateVisibilityForPlayer(player);
            
            player.sendMessage(messageManager.getHideSelfMessage(newValue));
        } else if (args.length >= 2) {
            // Admin command for other player
            if (!sender.hasPermission("nametags.command.admin.hide-self")) {
                sender.sendMessage(messageManager.getErrorMessage("no-permission"));
                return;
            }
            
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.getErrorMessage("player-not-found"));
                return;
            }
            
            // Check for -notify flag
            boolean notify = args.length >= 3 && args[2].equalsIgnoreCase("-notify");
            
            boolean newValue = preferencesManager.togglePreference(target, PreferencesManager.PreferenceType.HIDE_FROM_OTHERS);
            visibilityManager.updateVisibilityForPlayer(target);
            
            sender.sendMessage(messageManager.getAdminTargetMessage("hide-self", target.getName()));
            
            // Only notify target if -notify flag is present
            if (notify) {
                target.sendMessage(messageManager.getAdminNotificationMessage("hide", newValue));
            }
        }
    }

    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("nametags.command.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command")
                    .color(NamedTextColor.RED));
            return;
        }
        
        // Use existing reload logic from original command
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final var tag = plugin.getEntityManager().getNameTagEntity(player);
            if (tag != null) {
                tag.getTraits().destroy();
            }
        }

        plugin.reloadConfig();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final var tag = plugin.getEntityManager().removeEntity(player);
            if (tag != null) {
                tag.destroy();
            }

            final var newTag = plugin.getEntityManager().getOrCreateNameTagEntity(player);
            if (tag != null) {
                for (final var viewer : tag.getPassenger().getViewers()) {
                    newTag.getPassenger().addViewer(viewer);
                    Player playerViewer = Bukkit.getPlayer(viewer);
                    if (playerViewer != null) {
                        newTag.sendPassengerPacket(playerViewer);
                    }
                }
            }

            // Apply visibility preferences after reload
            visibilityManager.applyInitialVisibility(player);
        }
        
        sender.sendMessage(Component.text("Reloaded!").color(NamedTextColor.GREEN));
    }

    private void handleDebug(@NotNull CommandSender sender) {
        if (!sender.hasPermission("nametags.command.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command")
                    .color(NamedTextColor.RED));
            return;
        }
        
        // Use existing debug logic from original command
        sender.sendMessage(
                Component.text("NameTags debug")
                        .appendNewline()
                        .append(Component.text("Total NameTags: " + plugin.getEntityManager().getCacheSize())
                                .color(NamedTextColor.WHITE))
                        .appendNewline()
                        .append(Component.text("Cached last sent passengers: " + plugin.getEntityManager().getLastSentPassengersSize())
                                .color(NamedTextColor.WHITE))
                        .color(NamedTextColor.GOLD)
        );
    }

    private void handleRefresh(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("nametags.command.admin")) {
            sender.sendMessage(messageManager.getErrorMessage("no-permission"));
            return;
        }
        
        if (args.length == 1) {
            // Refresh all visibility
            visibilityManager.refreshAllVisibility();
            sender.sendMessage(Component.text("Refreshed visibility for all players").color(NamedTextColor.GREEN));
        } else if (args.length == 3) {
            // Refresh visibility between two specific players
            Player viewer = Bukkit.getPlayer(args[1]);
            Player target = Bukkit.getPlayer(args[2]);
            
            if (viewer == null || target == null) {
                sender.sendMessage(messageManager.getErrorMessage("player-not-found"));
                return;
            }
            
            visibilityManager.refreshVisibilityBetween(viewer, target);
            sender.sendMessage(Component.text("Refreshed visibility between " + viewer.getName() + " and " + target.getName())
                    .color(NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Usage: /nametags refresh [viewer] [target]")
                    .color(NamedTextColor.RED));
        }
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("NameTags Commands:")
                .color(NamedTextColor.GOLD)
                .appendNewline()
                .append(Component.text("/nametags toggle-others [player] [-notify] - Toggle visibility of other players' nametags")
                        .color(NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("/nametags preview [player] [-notify] - Toggle own nametag preview")
                        .color(NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("/nametags hide-self [player] [-notify] - Toggle hiding own nametag from others")
                        .color(NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("/nametags reload - Reload the plugin")
                        .color(NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("/nametags debug - Show debug information")
                        .color(NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("/nametags refresh [viewer] [target] - Refresh visibility (admin only)")
                        .color(NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Use -notify flag to send notification to target player")
                        .color(NamedTextColor.GRAY))
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            
            if (sender.hasPermission("nametags.command.toggle-others")) {
                subCommands.add("toggle-others");
            }
            if (sender.hasPermission("nametags.command.preview")) {
                subCommands.add("preview");
            }
            if (sender.hasPermission("nametags.command.hide-self")) {
                subCommands.add("hide-self");
            }
            if (sender.hasPermission("nametags.command.admin")) {
                subCommands.add("reload");
                subCommands.add("debug");
            }
            
            return subCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            // For admin commands, suggest player names
            if ((subCommand.equals("toggle-others") && sender.hasPermission("nametags.command.admin.toggle-others")) ||
                (subCommand.equals("preview") && sender.hasPermission("nametags.command.admin.preview")) ||
                (subCommand.equals("hide-self") && sender.hasPermission("nametags.command.admin.hide-self"))) {
                
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            // For admin commands, suggest -notify flag
            if ((subCommand.equals("toggle-others") && sender.hasPermission("nametags.command.admin.toggle-others")) ||
                (subCommand.equals("preview") && sender.hasPermission("nametags.command.admin.preview")) ||
                (subCommand.equals("hide-self") && sender.hasPermission("nametags.command.admin.hide-self"))) {
                
                if ("-notify".startsWith(args[2].toLowerCase())) {
                    return List.of("-notify");
                }
            }
        }
        
        return new ArrayList<>();
    }
}