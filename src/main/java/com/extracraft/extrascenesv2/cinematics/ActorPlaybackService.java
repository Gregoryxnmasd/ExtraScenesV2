package com.extracraft.extrascenesv2.cinematics;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ActorPlaybackService {

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Map<String, VirtualActor>> spawned = new HashMap<>();

    public ActorPlaybackService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void start(Player viewer, Cinematic cinematic, int tick) {
        start(viewer, cinematic, tick, null);
    }

    public void start(Player viewer, Cinematic cinematic, int tick, String excludedActorId) {
        cleanup(viewer);
        Map<String, VirtualActor> entities = new LinkedHashMap<>();
        for (SceneActor actor : cinematic.getActors().values()) {
            if (isExcluded(actor, excludedActorId) || !actor.isVisibleAtTick(tick)) {
                continue;
            }
            VirtualActor spawnedActor = spawnActor(viewer, actor, sample(actor.frames(), tick));
            if (spawnedActor != null) {
                entities.put(key(actor.id()), spawnedActor);
            }
        }
        spawned.put(viewer.getUniqueId(), entities);
    }

    public void tick(Player viewer, Cinematic cinematic, int tick) {
        tick(viewer, cinematic, tick, null);
    }

    public void tick(Player viewer, Cinematic cinematic, int tick, String excludedActorId) {
        Map<String, VirtualActor> entities = spawned.computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashMap<>());
        for (SceneActor actor : cinematic.getActors().values()) {
            String actorKey = key(actor.id());
            if (isExcluded(actor, excludedActorId) || !actor.isVisibleAtTick(tick)) {
                despawn(viewer, entities.remove(actorKey));
                continue;
            }

            Location next = sample(actor.frames(), tick);
            if (next == null || next.getWorld() == null) {
                despawn(viewer, entities.remove(actorKey));
                continue;
            }

            VirtualActor virtualActor = entities.get(actorKey);
            if (virtualActor == null) {
                virtualActor = spawnActor(viewer, actor, next);
                if (virtualActor != null) {
                    entities.put(actorKey, virtualActor);
                }
                continue;
            }

            if (!sameWorld(virtualActor.location(), next)) {
                despawn(viewer, virtualActor);
                VirtualActor respawned = spawnActor(viewer, actor, next);
                if (respawned != null) {
                    entities.put(actorKey, respawned);
                } else {
                    entities.remove(actorKey);
                }
                continue;
            }

            teleport(viewer, virtualActor, next);
        }
    }

    public void cleanup(Player viewer) {
        Map<String, VirtualActor> entities = spawned.remove(viewer.getUniqueId());
        if (entities == null) {
            return;
        }
        for (VirtualActor actor : entities.values()) {
            despawn(viewer, actor);
        }
    }

    private VirtualActor spawnActor(Player viewer, SceneActor actor, Location initial) {
        if (initial == null || initial.getWorld() == null) {
            return null;
        }

        int entityId = ThreadLocalRandom.current().nextInt(2_000_000_000);
        UUID profileId = UUID.randomUUID();
        VirtualActor virtualActor = new VirtualActor(entityId, profileId, initial.clone());

        WrappedGameProfile profile = new WrappedGameProfile(profileId, sanitizeProfileName(actor.displayName(), actor.id()));
        if (actor.skinTexture() != null && actor.skinSignature() != null) {
            profile.getProperties().put("textures", new WrappedSignedProperty("textures", actor.skinTexture(), actor.skinSignature()));
        }

        PacketContainer playerInfo = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        if (playerInfo.getPlayerInfoActions().size() > 0) {
            playerInfo.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
        } else if (playerInfo.getPlayerInfoAction().size() > 0) {
            playerInfo.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
        }
        if (playerInfo.getPlayerInfoDataLists().size() > 0) {
            playerInfo.getPlayerInfoDataLists().write(0, List.of(new PlayerInfoData(
                profile,
                0,
                EnumWrappers.NativeGameMode.SURVIVAL,
                WrappedChatComponent.fromText(actor.displayName())
            )));
        }
        sendPacket(viewer, playerInfo);

        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawn.getIntegers().write(0, entityId);
        spawn.getUUIDs().write(0, profileId);
        spawn.getDoubles().write(0, initial.getX());
        spawn.getDoubles().write(1, initial.getY());
        spawn.getDoubles().write(2, initial.getZ());
        spawn.getBytes().write(0, angleToByte(initial.getYaw()));
        spawn.getBytes().write(1, angleToByte(initial.getPitch()));
        sendPacket(viewer, spawn);

        PacketContainer metadata = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadata.getIntegers().write(0, entityId);
        List<WrappedDataValue> dataValues = new ArrayList<>();
        dataValues.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x20));
        dataValues.add(new WrappedDataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), true));
        metadata.getDataValueCollectionModifier().write(0, dataValues);
        sendPacket(viewer, metadata);

        return virtualActor;
    }

    private void teleport(Player viewer, VirtualActor actor, Location location) {
        PacketContainer teleport = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        teleport.getIntegers().write(0, actor.entityId());
        teleport.getDoubles().write(0, location.getX());
        teleport.getDoubles().write(1, location.getY());
        teleport.getDoubles().write(2, location.getZ());
        teleport.getBytes().write(0, angleToByte(location.getYaw()));
        teleport.getBytes().write(1, angleToByte(location.getPitch()));
        teleport.getBooleans().write(0, true);
        sendPacket(viewer, teleport);
        actor.location().setX(location.getX());
        actor.location().setY(location.getY());
        actor.location().setZ(location.getZ());
        actor.location().setYaw(location.getYaw());
        actor.location().setPitch(location.getPitch());
    }

    private void despawn(Player viewer, VirtualActor actor) {
        if (actor == null) {
            return;
        }

        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(actor.entityId()));
        sendPacket(viewer, destroy);

        PacketContainer removeInfo = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        removeInfo.getUUIDLists().write(0, List.of(actor.profileId()));
        sendPacket(viewer, removeInfo);
    }

    private void sendPacket(Player viewer, PacketContainer packet) {
        try {
            protocolManager.sendServerPacket(viewer, packet);
        } catch (Exception ex) {
            plugin.getLogger().warning("Actor packet failed for " + viewer.getName() + ": " + ex.getMessage());
        }
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

    private boolean isExcluded(SceneActor actor, String excludedActorId) {
        return excludedActorId != null && key(actor.id()).equals(key(excludedActorId));
    }

    private String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean sameWorld(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    private String sanitizeProfileName(String displayName, String fallbackId) {
        String base = (displayName == null || displayName.isBlank()) ? fallbackId : displayName;
        String stripped = base.replaceAll("[^A-Za-z0-9_]", "");
        if (stripped.isBlank()) {
            stripped = "SceneActor";
        }
        return stripped.length() <= 16 ? stripped : stripped.substring(0, 16);
    }

    private byte angleToByte(float angle) {
        return (byte) (angle * 256.0F / 360.0F);
    }

    private record VirtualActor(int entityId, UUID profileId, Location location) {}
}
