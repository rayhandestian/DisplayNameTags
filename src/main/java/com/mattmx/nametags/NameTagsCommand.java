package com.mattmx.nametags;

import com.mattmx.nametags.entity.NameTagEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class NameTagsCommand implements CommandExecutor {
    private final @NotNull NameTags plugin;

    public NameTagsCommand(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        this.plugin.reloadConfig();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final NameTagEntity tag = plugin.getEntityManager().removeEntity(player);

            if (tag == null) continue;

            tag.destroy();

            final NameTagEntity newTag = plugin.getEntityManager().getOrCreateNameTagEntity(player);

            for (final UUID viewer : tag.getPassenger().getViewers()) {
                newTag.getPassenger().addViewer(viewer);
            }

            newTag.updateVisibility();
        }

        sender.sendMessage(Component.text("Reloaded!").color(NamedTextColor.GREEN));
        return false;
    }
}
