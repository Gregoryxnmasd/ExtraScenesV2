package com.extracraft.extrascenesv2.cinematics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CinematicPlaybackService {

    private final JavaPlugin plugin;
    private final Map<UUID, PlaybackState> states = new HashMap<>();

    public CinematicPlaybackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInCinematic(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        return state != null && state.running;
    }

    public boolean play(Player player, Cinematic cinematic, int startTick, Integer endTick) {
        if (cinematic.isEmpty()) {
            return false;
        }

        int safeStart = Math.max(0, startTick);
        int maxEnd = Math.max(0, cinematic.getDurationTicks());
        int safeEnd = Math.max(safeStart, Math.min(endTick == null ? maxEnd : endTick, maxEnd));

        stop(player);

        PlaybackState state = new PlaybackState(cinematic, safeStart, safeEnd);
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
        return true;
    }

    public void stopAll() {
        for (UUID playerId : states.keySet().toArray(UUID[]::new)) {
            PlaybackState state = states.remove(playerId);
            if (state != null) {
                cancelTask(state);
            }
        }
    }

    public void handleDisconnect(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        cancelTask(state);
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

            if (state.currentTick > state.endTick) {
                stop(player);
                return;
            }

            Location destination = interpolateLocation(state.cinematic, state.currentTick);
            if (destination == null || destination.getWorld() == null) {
                stop(player);
                return;
            }

            player.teleport(destination);
            state.currentTick++;
        } catch (Exception ex) {
            plugin.getLogger().severe("Error en escena para " + player.getName() + ": " + ex.getMessage());
            stop(player);
        }
    }

    private Location interpolateLocation(Cinematic cinematic, int tick) {
        CinematicPoint prev = null;
        CinematicPoint next = null;

        for (CinematicPoint point : cinematic.getPoints()) {
            if (point.tick() <= tick) {
                prev = point;
            }
            if (point.tick() >= tick) {
                next = point;
                break;
            }
        }

        if (prev == null && next == null) {
            return null;
        }
        if (prev == null) {
            return next.location().clone();
        }
        if (next == null) {
            return prev.location().clone();
        }
        if (prev.tick() == next.tick()) {
            return prev.location().clone();
        }
        if (prev.location().getWorld() == null || next.location().getWorld() == null) {
            return null;
        }
        if (!prev.location().getWorld().equals(next.location().getWorld())) {
            return prev.location().clone();
        }

        double t = (tick - prev.tick()) / (double) (next.tick() - prev.tick());
        Location a = prev.location();
        Location b = next.location();

        double x = lerp(a.getX(), b.getX(), t);
        double y = lerp(a.getY(), b.getY(), t);
        double z = lerp(a.getZ(), b.getZ(), t);
        float yaw = (float) lerpAngle(a.getYaw(), b.getYaw(), t);
        float pitch = (float) lerp(a.getPitch(), b.getPitch(), t);

        return new Location(a.getWorld(), x, y, z, yaw, pitch);
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double lerpAngle(double start, double end, double t) {
        double delta = ((end - start + 540.0) % 360.0) - 180.0;
        return start + delta * t;
    }

    private static void cancelTask(PlaybackState state) {
        if (state.task != null) {
            state.task.cancel();
            state.task = null;
        }
    }

    private static final class PlaybackState {
        private final Cinematic cinematic;
        private final int endTick;
        private int currentTick;
        private boolean running;
        private BukkitTask task;

        private PlaybackState(Cinematic cinematic, int startTick, int endTick) {
            this.cinematic = cinematic;
            this.currentTick = startTick;
            this.endTick = endTick;
        }
    }
}
