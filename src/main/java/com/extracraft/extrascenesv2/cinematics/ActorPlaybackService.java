package com.extracraft.extrascenesv2.cinematics;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class ActorPlaybackService {

    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, UUID>> spawned = new HashMap<>();

    public ActorPlaybackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player viewer, Cinematic cinematic, int tick) {
        cleanup(viewer);
        Map<String, UUID> entities = new LinkedHashMap<>();
        for (SceneActor actor : cinematic.getActors().values()) {
            ArmorStand stand = spawnActor(viewer, actor, sample(actor.frames(), tick));
            if (stand != null) {
                entities.put(actor.id().toLowerCase(), stand.getUniqueId());
            }
        }
        spawned.put(viewer.getUniqueId(), entities);
    }

    public void tick(Player viewer, Cinematic cinematic, int tick) {
        Map<String, UUID> entities = spawned.get(viewer.getUniqueId());
        if (entities == null) {
            return;
        }
        for (SceneActor actor : cinematic.getActors().values()) {
            UUID entityId = entities.get(actor.id().toLowerCase());
            Location next = sample(actor.frames(), tick);
            if (entityId == null || next == null) {
                continue;
            }
            Entity entity = Bukkit.getEntity(entityId);
            if (!(entity instanceof ArmorStand stand) || !stand.isValid()) {
                continue;
            }
            stand.teleport(next);
        }
    }

    public void cleanup(Player viewer) {
        Map<String, UUID> entities = spawned.remove(viewer.getUniqueId());
        if (entities == null) {
            return;
        }
        for (UUID entityId : entities.values()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private ArmorStand spawnActor(Player viewer, SceneActor actor, Location initial) {
        if (initial == null || initial.getWorld() == null) {
            return null;
        }
        ArmorStand stand = (ArmorStand) initial.getWorld().spawnEntity(initial, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setAI(false);
        stand.setCanTick(false);
        stand.setCustomNameVisible(false);
        stand.setVisible(true);

        AttributeInstance scaleAttribute = stand.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(actor.scale());
        }

        if (actor.skinTexture() != null && actor.skinSignature() != null) {
            stand.getEquipment().setHelmet(createSkull(actor));
        }

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, stand);
            }
        }
        return stand;
    }

    private ItemStack createSkull(SceneActor actor) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), actor.displayName());
        profile.setProperty(new ProfileProperty("textures", actor.skinTexture(), actor.skinSignature()));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    private Location sample(List<ActorFrame> frames, int tick) {
        Location result = null;
        for (ActorFrame frame : frames) {
            if (frame.tick() > tick) {
                break;
            }
            result = frame.location();
        }
        return result == null ? null : result.clone();
    }
}
