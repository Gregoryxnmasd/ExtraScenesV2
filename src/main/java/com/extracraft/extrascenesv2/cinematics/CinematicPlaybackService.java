package com.extracraft.extrascenesv2.cinematics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CinematicPlaybackService {

    private static final String SPEED_MODIFIER_KEY = "cinematic_pumpkin_speed";

    private final JavaPlugin plugin;
    private final Map<UUID, PlaybackState> activeStates = new HashMap<>();

    public CinematicPlaybackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInCinematic(Player player) {
        return activeStates.containsKey(player.getUniqueId());
    }

    public boolean stop(Player player) {
        PlaybackState state = activeStates.remove(player.getUniqueId());
        if (state == null) {
            return false;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        restorePlayer(player, state);
        return true;
    }

    public boolean play(Player player, Cinematic cinematic) {
        if (cinematic.isEmpty()) {
            return false;
        }

        stop(player);

        PlaybackState state = new PlaybackState(cinematic);
        state.originalHelmet = player.getInventory().getHelmet();
        state.originalWalkSpeed = player.getWalkSpeed();
        player.setWalkSpeed(0f);
        equipCinematicPumpkin(player);
        activeStates.put(player.getUniqueId(), state);

        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player), 0L, 1L);
        return true;
    }

    public void stopAll() {
        for (UUID playerId : activeStates.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                activeStates.remove(playerId);
                continue;
            }
            stop(player);
        }
    }

    private void tick(Player player) {
        PlaybackState state = activeStates.get(player.getUniqueId());
        if (state == null || !player.isOnline()) {
            stop(player);
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
            Location loc = player.getLocation();
            player.setRotation(point.location().getYaw(), point.location().getPitch());
            if (loc.distanceSquared(point.location()) > 0.01) {
                player.teleport(point.location());
            }
        }

        state.ticksAtPoint++;
        if (state.ticksAtPoint >= point.durationTicks()) {
            state.pointIndex++;
            state.ticksAtPoint = 0;
        }
    }

    private void equipCinematicPumpkin(Player player) {
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        meta.setUnbreakable(true);
        meta.addAttributeModifier(
            Attribute.MOVEMENT_SPEED,
            new AttributeModifier(new NamespacedKey(plugin, SPEED_MODIFIER_KEY), -10.0, Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD)
        );
        pumpkin.setItemMeta(meta);
        player.getInventory().setHelmet(pumpkin);
    }

    private void restorePlayer(Player player, PlaybackState state) {
        player.getInventory().setHelmet(state.originalHelmet);
        player.setWalkSpeed(state.originalWalkSpeed);
    }

    private static final class PlaybackState {
        private final Cinematic cinematic;
        private int pointIndex;
        private int ticksAtPoint;
        private BukkitTask task;
        private ItemStack originalHelmet;
        private float originalWalkSpeed;

        private PlaybackState(Cinematic cinematic) {
            this.cinematic = cinematic;
        }
    }
}
