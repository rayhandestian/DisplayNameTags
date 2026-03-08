package com.mattmx.nametags.packet;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.entity.NameTagEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class PlayServerSetPassengersHandler {

    public static void handlePacket(@NotNull PacketSendEvent event) {
        final NameTags plugin = NameTags.getInstance();
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);

        final NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntityById(packet.getEntityId());

        if (nameTagEntity == null) return;

        // If the packet doesn't already contain our entity
        boolean containsNameTagPassenger = false;
        for (final int passengerId : packet.getPassengers()) {
            if (passengerId == nameTagEntity.getPassenger().getEntityId()) {
                containsNameTagPassenger = true;
            }
        }

        // TODO(Matt)?: Should we process async and then send another passenger packet afterwards?
        if (!containsNameTagPassenger) {

            // Add our entity
            int[] passengers = Arrays.copyOf(packet.getPassengers(), packet.getPassengers().length + 1);
            passengers[passengers.length - 1] = nameTagEntity.getPassenger().getEntityId();

            packet.setPassengers(passengers);

            NameTags.getInstance()
                .getEntityManager()
                .setLastSentPassengers(packet.getEntityId(), passengers);

            event.markForReEncode(true);
        }
        
        // Check visibility for the receiver - if they can't see the target, don't add the nametag passenger
        Player viewer = Bukkit.getPlayer(event.getUser().getUUID());
        if (viewer != null) {
            Player target = (Player) nameTagEntity.getBukkitEntity();
            if (target != null && !viewer.equals(target)) {
                boolean shouldSee = plugin.getVisibilityManager().shouldShowNametag(viewer, target);
                if (!shouldSee) {
                    // Remove the nametag passenger from the packet
                    int[] filteredPassengers = Arrays.stream(passengers)
                        .filter(id -> id != nameTagEntity.getPassenger().getEntityId())
                        .toArray();
                    packet.setPassengers(filteredPassengers);
                    NameTags.getInstance()
                        .getEntityManager()
                        .setLastSentPassengers(packet.getEntityId(), filteredPassengers);
                    event.markForReEncode(true);
                }
            }
        }
    }

}
