<div>

<h1 align="center">🏷️ Display Name Tags</h1>

</div>

Replace your players' boring old name tags with customizable ones based on 
text displays! (Thanks to [EntityLib](https://github.com/Tofaa2/EntityLib)!)


<p align="center">
    <img width="650px" src=nametags.gif />
</p>


## Features

### Player Visibility Controls
- **Toggle Others' Nametags**: Players can hide/show all other players' nametags
- **Preview Own Nametag**: Players can toggle visibility of their own nametag
- **Hide From Others**: Players can hide their nametag from other players (with admin override)

### Admin Management
- **Admin Override**: Admins can manage any player's visibility settings
- **Silent Commands**: Use `-notify` flag to optionally notify target players
- **See Hidden**: Admins can see nametags that players have hidden from others

## Commands

### Player Commands
```bash
/nametags toggle-others    # Toggle visibility of other players' nametags
/nametags preview          # Toggle own nametag preview
/nametags hide-self        # Hide own nametag from others
```

### Admin Commands
```bash
/nametags toggle-others <player> [-notify]  # Toggle for other player
/nametags preview <player> [-notify]        # Toggle preview for other player
/nametags hide-self <player> [-notify]      # Toggle hiding for other player
/nametags refresh [viewer] [target]         # Force visibility refresh
/nametags reload                            # Reload plugin
/nametags debug                             # Show debug information
```

## Permissions

### User Permissions (default: true)
- `nametags.command.toggle-others` - Toggle others' nametags
- `nametags.command.preview` - Toggle own nametag preview
- `nametags.command.hide-self` - Hide own nametag from others

### Admin Permissions (default: op)
- `nametags.command.admin.*` - Execute commands for other players
- `nametags.admin.see-hidden` - See nametags hidden by players
- `nametags.command.admin` - Access to reload and debug commands

### Permission Groups
- `nametags.user` - All basic user permissions
- `nametags.admin` - All admin permissions

## Configuration

You can customize default name tags, create grouped name tags, and configure messages.

Install the plugin and access the `plugins/NameTags/config.yml` for more information.

Player preferences are automatically saved to `plugins/NameTags/player-preferences.json`.

## API

Designed primarily for developers, the NameTags api gives you lightweight yet
powerful control over how the plugin operates.

You can override default behaviours using the `setDefaultProvider` method, and
the [NameTagEntityCreateEvent](./src/main/java/com/mattmx/nametags/event/NameTagEntityCreateEvent.java)
to hook into a tag's creation. You can add your own features using the 
[Trait](./src/main/java/com/mattmx/nametags/entity/trait/Trait.java) api.

```java

public void onEnable() {
    NameTags nameTags = NameTags.getInstance();
    
    // Override the default "base" settings of a tag.
    nameTags.getEntityManager()
        .setDefaultProvider((entity, meta) -> {
            meta.setText(Component.text(entity.getName()));
            /* ... */
        });
}

```

Here is an example where we can add an Item Display above the player's name tag
by using the `Trait` system.

```java

class MyCustomTrait extends Trait {
    // TODO create example by putting an ItemStack above a name tag.
    
    @Override
    public void onDisable() {
        // Clean up stuff
    }
}

class MyCustomListener implements Listener {
    
    @EventHandler
    public void onTagCreate(@NotNull NameTagEntityCreateEvent event) {
        if (!event.getBukkitEntity().getName().equals("MattMX")) return;
        
        event.getTraits().getOrAddTrait(MyCustomTrait.class, MyCustomTrait::new);
    }
    
}

```

<details>
    <summary>Kotlin example</summary>

Here is a brief example of Kotlin usage, and shows that you can use the nametags on entities other than just Players!

In this example, a dropped item will display a timer of 4 seconds before it is removed from the world, with a timer above it!

```kt
@EventHandler
fun onItemSpawn(event: ItemSpawnEvent) = event.apply {
    entity.isPersistent = false

    // Armour and tools should take longer to despawn
    val ticksTillRemove = 80 // 4 seconds

    val nameTagEntity = NameTags.getInstance()
        .entityManager
        .getOrCreateNameTagEntity(entity)

    nameTagEntity.modify { meta ->
        meta.isShadow = true
        meta.viewRange = 90f
        meta.backgroundColor = NameTags.TRANSPARENT
        meta.translation = Vector3f(0f, 0.45f, 0f)
        meta.billboardConstraints = AbstractDisplayMeta.BillboardConstraints.VERTICAL
        meta.textOpacity = (-180).toByte()
    }

    var counter = ticksTillRemove / 20L
    val update = runAsyncRepeat(20) {
        counter--
        nameTagEntity.modify { meta ->
            meta.text = Component.text(counter.toString()).color(NamedTextColor.RED)
        }
    }

    runSyncLater(ticksTillRemove) {
        update?.cancel()

        NameTags.getInstance()
            .entityManager
            .removeEntity(entity)
            ?.destroy()

        if (entity.isValid) {
            entity.remove()
        }
    }
}
```
    
</details>

## Data Storage

Player preferences are stored in JSON format with automatic optimization:
- Only players with non-default settings are saved to reduce file size
- Asynchronous saving prevents server lag
- Automatic cleanup when players quit

## Roadmap

- **GUI Interface**: Visual interface for managing preferences
- **Group Preferences**: Set preferences for entire permission groups
- **Temporary Settings**: Time-limited visibility toggles
- **Advanced Filters**: Filter nametags by various criteria
