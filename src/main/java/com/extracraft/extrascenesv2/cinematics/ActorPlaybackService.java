package com.extracraft.extrascenesv2.cinematics;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.plugin.java.JavaPlugin;

public final class ActorPlaybackService {

    private static final double RELATIVE_MOVE_THRESHOLD = 7.9D;
    private static final int MIN_ENTITY_ID = 200_000;
    private static final int MAX_ENTITY_ID = Integer.MAX_VALUE - 10_000;

    private static final long[] SCALE_RETRY_DELAYS = {1L, 5L, 20L};

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Map<String, VirtualActor>> spawned = new HashMap<>();
    private final AtomicInteger entitySequence = new AtomicInteger(ThreadLocalRandom.current().nextInt(MIN_ENTITY_ID, MIN_ENTITY_ID + 100_000));

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

            if (Math.abs(virtualActor.scale() - actor.scale()) > 0.0001D) {
                sendScaleAttribute(viewer, virtualActor.entityId(), actor.scale());
                virtualActor.setScale(actor.scale());
            }

            move(viewer, virtualActor, next);
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
        try {
            int entityId = nextEntityId();
            UUID profileId = UUID.randomUUID();
            String profileName = sanitizeProfileName(actor.displayName(), actor.id());
            VirtualActor virtualActor = new VirtualActor(entityId, profileId, profileName, initial.clone(), actor.scale());

            WrappedGameProfile profile = new WrappedGameProfile(profileId, profileName);
            if (actor.skinTexture() != null && actor.skinSignature() != null) {
                profile.getProperties().put("textures", new WrappedSignedProperty("textures", actor.skinTexture(), actor.skinSignature()));
            }

            if (!sendAddPlayerInfo(viewer, profileId, profile, actor.displayName())) {
                return null;
            }

            PacketContainer spawn = createSpawnPacket(entityId, profileId, initial);
            if (spawn == null || !sendPacket(viewer, spawn)) {
                removeFromPlayerInfo(viewer, profileId);
                return null;
            }

            sendMetadata(viewer, entityId);
            sendScaleAttribute(viewer, entityId, actor.scale());
            scheduleScaleRetries(viewer, actor.id(), entityId, actor.scale());
            hideNameTag(viewer, virtualActor);

            return virtualActor;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Unable to spawn actor '" + actor.id() + "' for " + viewer.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    private void move(Player viewer, VirtualActor actor, Location target) {
        Location current = actor.location();
        double deltaX = target.getX() - current.getX();
        double deltaY = target.getY() - current.getY();
        double deltaZ = target.getZ() - current.getZ();
        boolean requiresTeleport = Math.abs(deltaX) > RELATIVE_MOVE_THRESHOLD
            || Math.abs(deltaY) > RELATIVE_MOVE_THRESHOLD
            || Math.abs(deltaZ) > RELATIVE_MOVE_THRESHOLD;

        if (requiresTeleport) {
            teleport(viewer, actor.entityId(), target);
        } else {
            relativeMove(viewer, actor.entityId(), current, deltaX, deltaY, deltaZ, target.getYaw(), target.getPitch());
        }

        updateLocation(current, target);
    }

    private void teleport(Player viewer, int entityId, Location location) {
        PacketContainer teleport = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        teleport.getIntegers().write(0, entityId);
        teleport.getDoubles().write(0, location.getX());
        teleport.getDoubles().write(1, location.getY());
        teleport.getDoubles().write(2, location.getZ());
        teleport.getBytes().write(0, angleToByte(location.getYaw()));
        teleport.getBytes().write(1, angleToByte(location.getPitch()));
        if (teleport.getBooleans().size() > 0) {
            teleport.getBooleans().write(0, true);
        }
        sendPacket(viewer, teleport);
    }

    private void relativeMove(Player viewer, int entityId, Location current, double deltaX, double deltaY, double deltaZ, float yaw, float pitch) {
        PacketContainer move = protocolManager.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        move.getIntegers().write(0, entityId);
        move.getShorts().write(0, toRelativeShort(deltaX));
        move.getShorts().write(1, toRelativeShort(deltaY));
        move.getShorts().write(2, toRelativeShort(deltaZ));
        move.getBytes().write(0, angleToByte(yaw));
        move.getBytes().write(1, angleToByte(pitch));
        if (move.getBooleans().size() > 0) {
            move.getBooleans().write(0, true);
        }
        if (!sendPacket(viewer, move)) {
            teleport(viewer, entityId, new Location(current.getWorld(),
                current.getX() + deltaX,
                current.getY() + deltaY,
                current.getZ() + deltaZ,
                yaw,
                pitch));
        }
    }

    private void despawn(Player viewer, VirtualActor actor) {
        if (actor == null) {
            return;
        }

        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        if (destroy.getIntLists().size() > 0) {
            destroy.getIntLists().write(0, List.of(actor.entityId()));
        } else if (destroy.getIntegerArrays().size() > 0) {
            destroy.getIntegerArrays().write(0, new int[]{actor.entityId()});
        }
        sendPacket(viewer, destroy);

        removeFromPlayerInfo(viewer, actor.profileId());
        showNameTag(viewer, actor);
    }

    private void removeFromPlayerInfo(Player viewer, UUID profileId) {
        PacketContainer removeInfo = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        if (removeInfo.getUUIDLists().size() > 0) {
            removeInfo.getUUIDLists().write(0, List.of(profileId));
            sendPacket(viewer, removeInfo);
        }
    }

    private boolean sendPacket(Player viewer, PacketContainer packet) {
        try {
            protocolManager.sendServerPacket(viewer, packet);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Actor packet failed for " + viewer.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean sendAddPlayerInfo(Player viewer, UUID profileId, WrappedGameProfile profile, String displayName) {
        PacketContainer legacyInfo = createLegacyAddPlayerInfoPacket(profileId, profile, displayName);
        return legacyInfo != null && sendPacket(viewer, legacyInfo);
    }

    private void sendMetadata(Player viewer, int entityId) {
        PacketContainer metadata = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadata.getIntegers().write(0, entityId);

        List<WrappedDataValue> dataValues = new ArrayList<>();
        // Keep actors visible and do not force a floating name tag above their head.
        dataValues.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x00));
        dataValues.add(new WrappedDataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), false));
        // Enable all player skin model layers (hat, jacket, sleeves, pants, cape).
        dataValues.add(new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x7F));

        // NOTE:
        // For player entities, metadata index 12 is not the scale field on modern versions
        // (it is an Integer-based field). Writing a Float here causes the client to disconnect
        // with "Invalid entity data item type" when processing set_entity_data packets.
        //
        // Virtual actors are spawned as PLAYER entities, so we intentionally avoid forcing
        // scale through entity metadata.
        //
        // If scaling is required in the future, it must be sent via the proper attributes packet
        // (minecraft:scale) instead of metadata.

        if (metadata.getDataValueCollectionModifier().size() > 0) {
            metadata.getDataValueCollectionModifier().write(0, dataValues);
            sendPacket(viewer, metadata);
        }
    }

    private void sendScaleAttribute(Player viewer, int entityId, double actorScale) {
        try {
            PacketContainer attributesPacket = protocolManager.createPacket(PacketType.Play.Server.UPDATE_ATTRIBUTES);
            attributesPacket.getIntegers().write(0, entityId);

            WrappedAttribute wrappedAttribute = buildScaleAttribute(attributesPacket, actorScale);
            if (wrappedAttribute == null) {
                return;
            }

            if (attributesPacket.getAttributeCollectionModifier().size() > 0) {
                attributesPacket.getAttributeCollectionModifier().write(0, List.of(wrappedAttribute));
            } else if (attributesPacket.getSpecificModifier(Collection.class).size() > 0) {
                // Legacy fallback for ProtocolLib builds that still expose raw collections.
                attributesPacket.getSpecificModifier(Collection.class).write(0, List.of(wrappedAttribute));
            }

            sendPacket(viewer, attributesPacket);
        } catch (Throwable ex) {
            // Not all ProtocolLib builds expose attribute wrappers equally.
            plugin.getLogger().fine("Unable to send actor scale attribute: " + ex.getMessage());
        }
    }

    private void scheduleScaleRetries(Player viewer, String actorId, int entityId, double actorScale) {
        UUID viewerId = viewer.getUniqueId();
        String actorKey = key(actorId);
        for (long delay : SCALE_RETRY_DELAYS) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player onlineViewer = plugin.getServer().getPlayer(viewerId);
                if (onlineViewer == null || !onlineViewer.isOnline()) {
                    return;
                }

                Map<String, VirtualActor> actors = spawned.get(viewerId);
                if (actors == null) {
                    return;
                }

                VirtualActor current = actors.get(actorKey);
                if (current == null || current.entityId() != entityId) {
                    return;
                }

                sendScaleAttribute(onlineViewer, entityId, actorScale);
            }, delay);
        }
    }

    private WrappedAttribute buildScaleAttribute(PacketContainer attributesPacket, double actorScale) {
        for (String attributeKey : List.of("minecraft:scale", "minecraft:generic.scale")) {
            try {
                return WrappedAttribute.newBuilder()
                        .attributeKey(attributeKey)
                        .baseValue(actorScale)
                        .packet(attributesPacket)
                        .build();
            } catch (RuntimeException ignored) {
                // Try the legacy key fallback.
            }
        }

        return null;
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

    private short toRelativeShort(double delta) {
        return (short) Math.round(delta * 4096.0D);
    }

    private void updateLocation(Location current, Location target) {
        current.setX(target.getX());
        current.setY(target.getY());
        current.setZ(target.getZ());
        current.setYaw(target.getYaw());
        current.setPitch(target.getPitch());
    }

    private int nextEntityId() {
        int next = entitySequence.incrementAndGet();
        if (next >= MAX_ENTITY_ID) {
            entitySequence.set(MIN_ENTITY_ID);
            return entitySequence.incrementAndGet();
        }
        return next;
    }

    private PacketContainer createSpawnPacket(int entityId, UUID profileId, Location initial) {
        try {
            PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
            spawn.getIntegers().write(0, entityId);
            spawn.getUUIDs().write(0, profileId);
            spawn.getDoubles().write(0, initial.getX());
            spawn.getDoubles().write(1, initial.getY());
            spawn.getDoubles().write(2, initial.getZ());
            spawn.getBytes().write(0, angleToByte(initial.getYaw()));
            spawn.getBytes().write(1, angleToByte(initial.getPitch()));
            return spawn;
        } catch (RuntimeException legacySpawnUnavailable) {
            try {
                PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
                spawn.getIntegers().write(0, entityId);
                spawn.getUUIDs().write(0, profileId);
                spawn.getEntityTypeModifier().write(0, EntityType.PLAYER);
                spawn.getDoubles().write(0, initial.getX());
                spawn.getDoubles().write(1, initial.getY());
                spawn.getDoubles().write(2, initial.getZ());
                spawn.getBytes().write(0, angleToByte(initial.getPitch()));
                spawn.getBytes().write(1, angleToByte(initial.getYaw()));
                return spawn;
            } catch (RuntimeException modernSpawnFailed) {
                plugin.getLogger().warning("Unable to build actor spawn packet for profile " + profileId + ": " + modernSpawnFailed.getMessage());
                return null;
            }
        }
    }

    private PacketContainer createLegacyAddPlayerInfoPacket(UUID profileId, WrappedGameProfile profile, String displayName) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        WrappedChatComponent chatDisplayName = WrappedChatComponent.fromText(displayName == null ? "" : displayName);

        try {
            if (packet.getPlayerInfoActions().size() > 0) {
                packet.getPlayerInfoActions().write(0, Collections.singleton(EnumWrappers.PlayerInfoAction.ADD_PLAYER));
            } else if (packet.getPlayerInfoAction().size() > 0) {
                packet.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
            }

            List<PlayerInfoData> data = Collections.singletonList(new PlayerInfoData(
                profileId,
                0,
                true,
                NativeGameMode.SURVIVAL,
                profile,
                chatDisplayName
            ));

            if (packet.getLists(PlayerInfoData.getConverter()).size() > 0) {
                packet.getLists(PlayerInfoData.getConverter()).write(0, data);
            } else if (packet.getPlayerInfoDataLists().size() > 0) {
                packet.getPlayerInfoDataLists().write(0, data);
            }

            return packet;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Unable to build PLAYER_INFO packet for actor " + profile.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    private void hideNameTag(Player viewer, VirtualActor actor) {
        Scoreboard scoreboard = viewer.getScoreboard();
        if (scoreboard == null) {
            return;
        }

        try {
            Team team = scoreboard.getTeam(actor.teamId());
            if (team == null) {
                team = scoreboard.registerNewTeam(actor.teamId());
            }

            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            if (!team.hasEntry(actor.profileName())) {
                team.addEntry(actor.profileName());
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Skipping name tag hide for actor '" + actor.profileName() + "': " + ex.getMessage());
        }
    }

    private void showNameTag(Player viewer, VirtualActor actor) {
        Scoreboard scoreboard = viewer.getScoreboard();
        if (scoreboard == null) {
            return;
        }

        try {
            Team team = scoreboard.getTeam(actor.teamId());
            if (team == null) {
                return;
            }

            team.removeEntry(actor.profileName());
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Skipping name tag reset for actor '" + actor.profileName() + "': " + ex.getMessage());
        }
    }


    private static final class VirtualActor {
        private final int entityId;
        private final UUID profileId;
        private final String profileName;
        private final Location location;
        private double scale;

        private VirtualActor(int entityId, UUID profileId, String profileName, Location location, double scale) {
            this.entityId = entityId;
            this.profileId = profileId;
            this.profileName = profileName;
            this.location = location;
            this.scale = scale;
        }

        private int entityId() {
            return entityId;
        }

        private UUID profileId() {
            return profileId;
        }

        private String profileName() {
            return profileName;
        }

        private Location location() {
            return location;
        }

        private double scale() {
            return scale;
        }

        private void setScale(double scale) {
            this.scale = scale;
        }

        private String teamId() {
            return "esv2_" + entityId;
        }
    }
}
