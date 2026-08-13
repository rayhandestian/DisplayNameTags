package com.mattmx.nametags.entity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.retrooper.packetevents.util.Vector3f;
import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.event.NameTagEntityCreateEvent;
import com.mattmx.nametags.event.NameTagEntityPreSpawnEvent;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class NameTagEntityManager {

    private final Cache<UUID, NameTagEntity> nameTagCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(1))
        .removalListener(this::handleRemoval)
        .build();

    private final ConcurrentHashMap<Integer, NameTagEntity> nameTagEntityByEntityId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, NameTagEntity> nameTagEntityByPassengerEntityId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, int[]> lastSentPassengers = new ConcurrentHashMap<>();

    private @NotNull BiConsumer<Entity, TextDisplayMeta> defaultProvider = (entity, meta) -> {
        meta.setText(entity.name());
        meta.setTranslation(new Vector3f(0f, 0.2f, 0f));
        meta.setBillboardConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
        meta.setViewRange(50f);
    };

    public @NotNull NameTagEntity getOrCreateNameTagEntity(@NotNull Entity entity) {
        NameTagEntity tagEntity = nameTagCache.get(entity.getUniqueId(), uuid -> {
            NameTagEntity newlyCreated = new NameTagEntity(entity);

            newlyCreated.getPassenger().consumeEntityMeta(TextDisplayMeta.class, meta ->
                defaultProvider.accept(entity, meta)
            );

            Bukkit.getPluginManager().callEvent(new NameTagEntityPreSpawnEvent(newlyCreated));

            newlyCreated.initialize();

            Bukkit.getPluginManager().callEvent(new NameTagEntityCreateEvent(newlyCreated));

            nameTagEntityByEntityId.put(entity.getEntityId(), newlyCreated);
            nameTagEntityByPassengerEntityId.put(newlyCreated.getPassenger().getEntityId(), newlyCreated);

            return newlyCreated;
        });
        Objects.requireNonNull(tagEntity, "Cache.get(…) unexpectedly returned null for UUID " + entity.getUniqueId());

        // Safety net. A cache hit whose passenger is despawned can never be revived, because
        // spawn() only runs inside the loader above and this value skipped it. Handing it out
        // produces a nametag that is invisible to everyone with no error anywhere, so make the
        // failure loud instead of silent if anything ever re-introduces that state.
        if (!tagEntity.getPassenger().isSpawned()) {
            NameTags.getInstance().getLogger().warning(
                "Cached nametag for " + entity.getUniqueId() + " is despawned and cannot be revived."
                    + " Its nametag will be invisible to every viewer. This is a bug, please report it."
            );
        }

        return tagEntity;
    }

    public @Nullable NameTagEntity removeEntity(@NotNull Entity entity) {
        final NameTagEntity nameTagEntity = nameTagCache.getIfPresent(entity.getUniqueId());

        // Must do the full cleanup itself, as 1.5.6 did inline. handleRemoval no longer acts on
        // the EXPLICIT notification this fires, so nothing else will.
        //
        // Clearing lastSentPassengers is the load-bearing part: NameTagEntity.getPassengersPacket()
        // prefers that cached array over recomputing, so a stale entry makes both repair paths
        // (NameTagsSubCommand#handleReload and PlayServerSpawnEntityHandler) send a SetPassengers
        // naming the destroyed display's entity id. SetPassengers replaces the passenger list, so
        // that also unmounts whatever the client did have.
        removeEntirely(entity);

        return nameTagEntity;
    }

    private void removeEntirely(@NotNull Entity entity) {
        lastSentPassengers.remove(entity.getEntityId());
        nameTagCache.invalidate(entity.getUniqueId());

        final NameTagEntity removed = nameTagEntityByEntityId.remove(entity.getEntityId());
        if (removed != null) {
            nameTagEntityByPassengerEntityId.remove(removed.getPassenger().getEntityId());
        }
    }

    public @Nullable NameTagEntity getNameTagEntity(@NotNull Entity entity) {
        return nameTagCache.getIfPresent(entity.getUniqueId());
    }

    public @Nullable NameTagEntity getNameTagEntityByUUID(UUID uuid) {
        return nameTagCache.getIfPresent(uuid);
    }

    public @Nullable NameTagEntity getNameTagEntityById(int entityId) {
        return nameTagEntityByEntityId.get(entityId);
    }

    public @Nullable NameTagEntity getNameTagEntityByTagEntityId(int tagEntityId) {
        return nameTagEntityByPassengerEntityId.get(tagEntityId);
    }

    public @NotNull Map<UUID, NameTagEntity> getMappedEntities() {
        return nameTagCache.asMap();
    }

    public @NotNull Collection<NameTagEntity> getAllEntities() {
        return nameTagCache.asMap().values();
    }

    public void setDefaultProvider(@NotNull BiConsumer<Entity, TextDisplayMeta> consumer) {
        this.defaultProvider = consumer;
    }

    public void setLastSentPassengers(int entityId, int[] passengers) {
        this.lastSentPassengers.put(entityId, passengers);
    }

    public void removeLastSentPassengersCache(int entityId) {
        this.lastSentPassengers.remove(entityId);
    }

    public @NotNull Optional<int[]> getLastSentPassengers(int entityId) {
        return Optional.ofNullable(this.lastSentPassengers.get(entityId));
    }

    public int getCacheSize() {
        return nameTagCache.asMap().size();
    }

    public int getEntityIdMapSize() {
        return nameTagEntityByEntityId.size();
    }

    public int getPassengerIdMapSize() {
        return nameTagEntityByPassengerEntityId.size();
    }

    public int getLastSentPassengersSize() {
        return lastSentPassengers.size();
    }

    private void handleRemoval(UUID uuid, NameTagEntity tagEntity, RemovalCause cause) {
        if (uuid == null || tagEntity == null) {
            return;
        }

        // EXPIRED is the only eviction this class owns. For every other cause somebody else
        // already owns the entry's lifecycle, and the re-insert below is destructive:
        //
        //   REPLACED -> the value was overwritten rather than evicted, so there is nothing to
        //   clean up. Acting on it is fatal: both branches below re-insert with
        //   nameTagCache.put(), and a put over a key that is still present fires another
        //   REPLACED notification, which Caffeine dispatches to ForkJoinPool.commonPool, which
        //   calls this method again, which puts again. The loop sustains itself and pins every
        //   common-pool worker, starving everything else that shares that pool (ExcellentEconomy
        //   resolves command targets there, so /ethea give took tens of minutes to apply).
        //
        //   EXPLICIT -> removeEntity() deliberately dropped the entry and the caller is about to
        //   call destroy() on it. Re-inserting resurrects a despawned WrapperEntity, and a
        //   despawned wrapper can never be revived: spawn() is only reachable from
        //   NameTagEntity.initialize(), which only runs inside the cache loader above, and a
        //   value already in the cache never goes through the loader again. addViewer() on it
        //   sends no packets and logs nothing, while removeViewer() still fires a real
        //   DestroyEntities. The UUID cache then holds a corpse while nameTagEntityByEntityId
        //   holds the live tag, so client untracking works and retracking silently does nothing.
        //   That is the "all nametags vanish until /nametags reload, then vanish again on
        //   teleport" regression. 1.5.6 guarded this with `cause != RemovalCause.EXPIRED`.
        if (cause != RemovalCause.EXPIRED) {
            return;
        }

        Entity entity = tagEntity.getBukkitEntity();

        if (entity instanceof Player player) {
            if (!player.isOnline() || !player.isConnected()) {
                tagEntity.destroy();
                removeEntirely(entity);
                // Actually remove from map
                this.nameTagCache.cleanUp();
            } else {
                this.nameTagCache.put(uuid, tagEntity);
            }
        } else {
            // Must be run on the main thread, so sync this call
            Bukkit.getScheduler().runTask(NameTags.getInstance(), () -> {
                if (Bukkit.getEntity(uuid) == null) {
                    tagEntity.destroy();
                    removeEntirely(entity);
                    this.nameTagCache.cleanUp();
                } else {
                    this.nameTagCache.put(uuid, tagEntity);
                }
            });
        }
    }
}
