package com.extracraft.extrascenesv2.cinematics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CinematicPlaybackService {

    private static final String SPEED_MODIFIER_KEY = "cinematic_pumpkin_speed";

    private final JavaPlugin plugin;
    private final Map<UUID, PlaybackState> states = new HashMap<>();

    public CinematicPlaybackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInCinematic(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        return state != null && state.running;
    }

    public boolean play(Player player, Cinematic cinematic) {
        if (cinematic.isEmpty()) {
            return false;
        }

        stop(player);

        PlaybackState state = new PlaybackState(cinematic);
        states.put(player.getUniqueId(), state);
        startRunning(player, state);
        return true;
    }

    public boolean stop(Player player) {
        PlaybackState state = states.remove(player.getUniqueId());
        if (state == null) {
            return false;
        }

        cancelTask(state);
        cleanupPlayerEffects(player, state);
        return true;
    }

    public void stopAll() {
        for (UUID playerId : states.keySet().toArray(UUID[]::new)) {
            PlaybackState state = states.remove(playerId);
            if (state == null) {
                continue;
            }
            cancelTask(state);

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                cleanupPlayerEffects(player, state);
            }
        }
    }

    public void handleDisconnect(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        cancelTask(state);
        cleanupPlayerEffects(player, state);
        state.running = false;
    }

    public void handleJoin(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null || state.running) {
            return;
        }

        startRunning(player, state);
    }

    private void startRunning(Player player, PlaybackState state) {
        state.running = true;
        applyPlayerEffects(player, state);
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player), 0L, 1L);
    }

    private void tick(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null || !state.running) {
            return;
        }

        try {
            if (!player.isOnline()) {
                handleDisconnect(player);
                return;
            }

            if (state.pointIndex >= state.cinematic.getPoints().size()) {
                stop(player);
                return;
            }

            CinematicPoint point = state.cinematic.getPoints().get(state.pointIndex);
            if (state.ticksAtPoint == 0) {
                Location destination = point.location().clone();
                if (destination.getWorld() == null) {
                    stop(player);
                    return;
                }
                player.teleport(destination);
            }

            if (point.cameraMode() == CinematicPoint.CameraMode.LOCKED) {
                player.setRotation(point.location().getYaw(), point.location().getPitch());
                if (player.getLocation().distanceSquared(point.location()) > 0.01) {
                    player.teleport(point.location());
                }
            }

            state.ticksAtPoint++;
            if (state.ticksAtPoint >= point.durationTicks()) {
                state.pointIndex++;
                state.ticksAtPoint = 0;
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Error en cinemática para " + player.getName() + ": " + ex.getMessage());
            stop(player);
        }
    }

    private void applyPlayerEffects(Player player, PlaybackState state) {
        state.originalWalkSpeed = player.getWalkSpeed();

        ItemStack fakePumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        player.sendEquipmentChange(player, EquipmentSlot.HEAD, fakePumpkin);

        AttributeInstance movement = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement != null) {
            removeSpeedModifier(movement, state);
            AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey(plugin, SPEED_MODIFIER_KEY),
                -10.0,
                Operation.ADD_NUMBER,
                EquipmentSlotGroup.HEAD
            );
            movement.addModifier(modifier);
            state.appliedSpeedModifier = modifier;
        }
    }

    private void cleanupPlayerEffects(Player player, PlaybackState state) {
        player.sendEquipmentChange(player, EquipmentSlot.HEAD, player.getInventory().getHelmet());

        AttributeInstance movement = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement != null) {
            removeSpeedModifier(movement, state);
        }

        player.setWalkSpeed(state.originalWalkSpeed);
    }

    private void removeSpeedModifier(AttributeInstance movement, PlaybackState state) {
        if (state.appliedSpeedModifier != null) {
            movement.removeModifier(state.appliedSpeedModifier);
            state.appliedSpeedModifier = null;
        }
    }

    private static void cancelTask(PlaybackState state) {
        if (state.task != null) {
            state.task.cancel();
            state.task = null;
        }
    }

    private static final class PlaybackState {
        private final Cinematic cinematic;
        private int pointIndex;
        private int ticksAtPoint;
        private boolean running;
        private BukkitTask task;
        private float originalWalkSpeed;
        private AttributeModifier appliedSpeedModifier;

        private PlaybackState(Cinematic cinematic) {
            this.cinematic = cinematic;
        }
    }
}
