package com.extracraft.extrascenesv2.cinematics;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        start(viewer, cinematic, tick, null);
    }

    public void start(Player viewer, Cinematic cinematic, int tick, String excludedActorId) {
        cleanup(viewer);
        Map<String, UUID> entities = new LinkedHashMap<>();
        for (SceneActor actor : cinematic.getActors().values()) {
            if (isExcluded(actor, excludedActorId) || !actor.isVisibleAtTick(tick)) {
                continue;
            }
            ArmorStand stand = spawnActor(viewer, actor, sample(actor.frames(), tick));
            if (stand != null) {
                entities.put(key(actor.id()), stand.getUniqueId());
            }
        }
        spawned.put(viewer.getUniqueId(), entities);
    }

    public void tick(Player viewer, Cinematic cinematic, int tick) {
        tick(viewer, cinematic, tick, null);
    }

    public void tick(Player viewer, Cinematic cinematic, int tick, String excludedActorId) {
        Map<String, UUID> entities = spawned.computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashMap<>());
        for (SceneActor actor : cinematic.getActors().values()) {
            String actorKey = key(actor.id());
            if (isExcluded(actor, excludedActorId) || !actor.isVisibleAtTick(tick)) {
                despawn(entities.remove(actorKey));
                continue;
            }

            Location next = sample(actor.frames(), tick);
            if (next == null || next.getWorld() == null) {
                despawn(entities.remove(actorKey));
                continue;
            }

            UUID entityId = entities.get(actorKey);
            Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
            if (!(entity instanceof ArmorStand stand) || !stand.isValid()) {
                ArmorStand created = spawnActor(viewer, actor, next);
                if (created != null) {
                    entities.put(actorKey, created.getUniqueId());
                }
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
            despawn(entityId);
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
        stand.customName(null);
        stand.setVisible(true);
        stand.setMarker(false);

        AttributeInstance scaleAttribute = stand.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(actor.scale());
        }

        if (actor.skinTexture() != null && actor.skinSignature() != null && stand.getEquipment() != null) {
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
        ActorFrame prev = null;
        ActorFrame next = null;
        for (ActorFrame frame : frames) {
            if (frame.tick() <= tick) {
                prev = frame;
                continue;
            }
            next = frame;
            break;
        }

        if (prev == null && next == null) {
            return null;
        }
        if (prev == null) {
            return next.location();
        }
        if (next == null) {
            return prev.location();
        }

        Location a = prev.location();
        Location b = next.location();
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return a;
        }

        double t = (tick - prev.tick()) / (double) Math.max(1, next.tick() - prev.tick());
        double x = a.getX() + (b.getX() - a.getX()) * t;
        double y = a.getY() + (b.getY() - a.getY()) * t;
        double z = a.getZ() + (b.getZ() - a.getZ()) * t;
        float yaw = (float) (a.getYaw() + (b.getYaw() - a.getYaw()) * t);
        float pitch = (float) (a.getPitch() + (b.getPitch() - a.getPitch()) * t);
        return new Location(a.getWorld(), x, y, z, yaw, pitch);
    }

    private void despawn(UUID entityId) {
        if (entityId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private boolean isExcluded(SceneActor actor, String excludedActorId) {
        return excludedActorId != null && key(actor.id()).equals(key(excludedActorId));
    }

    private String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
