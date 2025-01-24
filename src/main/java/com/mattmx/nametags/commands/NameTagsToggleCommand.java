package com.mattmx.nametags.commands;

import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.entity.NameTagEntity;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NameTagsToggleCommand implements CommandExecutor, TabCompleter {
    private final NameTags plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final FileConfiguration messages;

    public NameTagsToggleCommand(NameTags plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml!");
                e.printStackTrace();
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("nametags.command.toggle")) {
            sender.sendMessage(getMessage("nametags.toggle.no-permission"));
            return true;
        }

        Player targetPlayer;
        boolean toggleState;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMessage("nametags.toggle.usage"));
                return true;
            }
            targetPlayer = (Player) sender;
            toggleState = !isNameTagsHidden(targetPlayer);
            
            // Show current state before toggling
            String currentState = isNameTagsHidden(targetPlayer) ? "OFF" : "ON";
            sender.sendMessage(getMessage("nametags.toggle.current-state").replace("{state}", currentState));
        } else {
            if (args[0].equalsIgnoreCase("on")) {
                toggleState = false;
            } else if (args[0].equalsIgnoreCase("off")) {
                toggleState = true;
            } else {
                sender.sendMessage(getMessage("nametags.toggle.usage"));
                return true;
            }

            if (args.length > 1) {
                if (!sender.hasPermission("nametags.command.toggle.others")) {
                    sender.sendMessage(getMessage("nametags.toggle.no-permission-others"));
                    return true;
                }
                targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(getMessage("nametags.toggle.player-not-found"));
                    return true;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("nametags.toggle.usage"));
                    return true;
                }
                targetPlayer = (Player) sender;
            }
        }

        setNameTagsHidden(targetPlayer, toggleState);
        
        String message;
        if (targetPlayer == sender) {
            message = getMessage(toggleState ? "nametags.toggle.toggle-off" : "nametags.toggle.toggle-on");
        } else {
            message = getMessage(toggleState ? "nametags.toggle.other-off" : "nametags.toggle.other-on")
                    .replace("{player}", targetPlayer.getName());
        }
        sender.sendMessage(message);
        
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("nametags.command.toggle")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("on", "off").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && sender.hasPermission("nametags.command.toggle.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    public boolean isNameTagsHidden(Player player) {
        return data.getBoolean(player.getUniqueId() + ".toggle-off", false);
    }

    public void setNameTagsHidden(Player player, boolean hidden) {
        String path = player.getUniqueId().toString();
        if (hidden) {
            data.set(path + ".toggle-off", true);
            // Remove player as viewer from all nametag entities
            for (NameTagEntity entity : plugin.getEntityManager().getAllEntities()) {
                // Skip the player's own nametag
                if (entity.getBukkitEntity().getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                entity.getPassenger().removeViewer(player.getUniqueId());
                entity.updateVisibility();
            }
        } else {
            if (data.contains(path + ".toggle-off")) {
                data.set(path + ".toggle-off", null);
            }
            if (data.getConfigurationSection(path) != null && data.getConfigurationSection(path).getKeys(false).isEmpty()) {
                data.set(path, null);
            }
            // Add player as viewer to all nametag entities and force update
            for (NameTagEntity entity : plugin.getEntityManager().getAllEntities()) {
                // Skip the player's own nametag
                if (entity.getBukkitEntity().getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                entity.getPassenger().addViewer(player.getUniqueId());
                entity.updateVisibility();
                // Force send the passenger packet to update the nametag for the player
                entity.sendPassengerPacket(player);
            }
        }
        saveData();
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml!");
            e.printStackTrace();
        }
    }

    private String getMessage(String path) {
        return messages.getString(path, "Message not found: " + path).replace('&', '§');
    }

    public void reloadData() {
        data = YamlConfiguration.loadConfiguration(dataFile);
    }
} 