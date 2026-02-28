package com.extracraft.extrascenesv2.cinematics;

import com.extracraft.extrascenesv2.audio.OpenAudioCommandService;
import com.extracraft.extrascenesv2.placeholders.PlaceholderResolver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CinematicPlaybackService {

    private static final NamespacedKey PUMPKIN_SPEED_PENALTY_KEY =
            NamespacedKey.fromString("extrascenesv2:cinematic_pumpkin_speed_penalty");

    private final JavaPlugin plugin;
    private final Map<UUID, PlaybackState> states = new HashMap<>();
    private final Map<UUID, Set<String>> playedCinematics = new HashMap<>();
    private final PlaceholderResolver placeholderResolver;
    private final ActorPlaybackService actorPlaybackService;
    private final OpenAudioCommandService openAudioCommandService;
    private final Map<UUID, String> subtitleLine1 = new HashMap<>();
    private final Map<UUID, String> subtitleLine2 = new HashMap<>();
    private static final double BEZIER_TENSION = 0.82;

    public CinematicPlaybackService(JavaPlugin plugin) {
        this(plugin, new PlaceholderResolver(), new ActorPlaybackService(plugin));
    }

    public CinematicPlaybackService(JavaPlugin plugin, PlaceholderResolver placeholderResolver, ActorPlaybackService actorPlaybackService) {
        this(plugin, placeholderResolver, actorPlaybackService, new OpenAudioCommandService(plugin));
    }

    public CinematicPlaybackService(JavaPlugin plugin, PlaceholderResolver placeholderResolver,
                                    ActorPlaybackService actorPlaybackService, OpenAudioCommandService openAudioCommandService) {
        this.plugin = plugin;
        this.placeholderResolver = placeholderResolver;
        this.actorPlaybackService = actorPlaybackService;
        this.openAudioCommandService = openAudioCommandService;
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

        boolean fullPlayback = safeStart == 0 && safeEnd >= maxEnd;
        PlaybackState state = new PlaybackState(cinematic, safeStart, safeEnd, fullPlayback, player.getLocation(), player.getGameMode());
        states.put(player.getUniqueId(), state);
        subtitleLine1.put(player.getUniqueId(), "");
        subtitleLine2.put(player.getUniqueId(), "");
        startRunning(player, state);
        actorPlaybackService.start(player, state.cinematic, state.currentTick);
        startAudio(player, state);
        runLifecycleCommands(player, state, state.cinematic.getStartCommands(), state.currentTick);
        return true;
    }

    public boolean hasPlayerPlayed(String cinematicId, UUID playerId) {
        if (cinematicId == null || cinematicId.isBlank() || playerId == null) {
            return false;
        }

        Set<String> played = playedCinematics.get(playerId);
        return played != null && played.contains(normalizeId(cinematicId));
    }

    public int getPlayedCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }

        Set<String> played = playedCinematics.get(playerId);
        return played == null ? 0 : played.size();
    }

    public Set<String> getPlayedSceneIds(UUID playerId) {
        if (playerId == null) {
            return Set.of();
        }

        Set<String> played = playedCinematics.get(playerId);
        if (played == null || played.isEmpty()) {
            return Set.of();
        }

        return Collections.unmodifiableSet(new HashSet<>(played));
    }

    public String getCurrentSceneId(UUID playerId) {
        PlaybackState state = states.get(playerId);
        if (state == null) {
            return "";
        }
        return state.cinematic.getId();
    }

    public int getCurrentTick(UUID playerId) {
        PlaybackState state = states.get(playerId);
        if (state == null) {
            return 0;
        }
        return Math.max(0, state.currentTick);
    }

    public int getCurrentEndTick(UUID playerId) {
        PlaybackState state = states.get(playerId);
        if (state == null) {
            return 0;
        }
        return Math.max(0, state.endTick);
    }

    public boolean hasPlaybackState(UUID playerId) {
        return playerId != null && states.containsKey(playerId);
    }

    public String getSubtitleLine(UUID playerId, int line) {
        if (playerId == null) {
            return "";
        }
        return line == 2 ? subtitleLine2.getOrDefault(playerId, "") : subtitleLine1.getOrDefault(playerId, "");
    }

    public void syncSubtitleForTick(Player player, Cinematic cinematic, int tick) {
        if (player == null || cinematic == null) {
            return;
        }
        CinematicSubtitleCue cue = cinematic.getSubtitleAtTick(Math.max(0, tick));
        subtitleLine1.put(player.getUniqueId(), cue == null ? "" : cue.line1());
        subtitleLine2.put(player.getUniqueId(), cue == null ? "" : cue.line2());
    }

    public void clearSubtitleLines(UUID playerId) {
        if (playerId == null) {
            return;
        }
        subtitleLine1.remove(playerId);
        subtitleLine2.remove(playerId);
    }

    public boolean stop(Player player) {
        return stop(player, false);
    }

    public boolean stop(Player player, boolean runEndCommands) {
        PlaybackState state = states.remove(player.getUniqueId());
        if (state == null) {
            return false;
        }

        if (runEndCommands) {
            runLifecycleCommands(player, state, state.cinematic.getEndCommands(), Math.max(0, state.currentTick));
        }

        cancelSeekTransition(state);
        cancelTask(state);
        restoreHiddenPlayers(player, state);
        clearFakeHelmet(player);
        restoreGameMode(player, state);
        actorPlaybackService.cleanup(player);
        subtitleLine1.remove(player.getUniqueId());
        subtitleLine2.remove(player.getUniqueId());
        stopAudio(player, state);
        return true;
    }

    public boolean pause(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null || !state.running) {
            return false;
        }
        cancelSeekTransition(state);
        cancelTask(state);
        state.running = false;
        stopAudio(player, state);
        return true;
    }

    public boolean resume(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null || state.running) {
            return false;
        }
        startRunning(player, state);
        actorPlaybackService.start(player, state.cinematic, state.currentTick);
        startAudio(player, state);
        runLifecycleCommands(player, state, state.cinematic.getStartCommands(), state.currentTick);
        return true;
    }

    public boolean seek(Player player, int targetTick) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null) {
            return false;
        }

        int clampedTick = Math.max(0, Math.min(targetTick, state.endTick));
        int fromTick = Math.max(0, state.currentTick);
        if (fromTick == clampedTick) {
            renderAtTick(player, state, clampedTick, false);
            return true;
        }

        cancelSeekTransition(state);
        int distance = Math.abs(clampedTick - fromTick);
        int transitionSteps = Math.max(3, Math.min(40, Math.max(1, distance / 2)));
        startSeekTransition(player, state, fromTick, clampedTick, transitionSteps);

        if (state.running) {
            stopAudio(player, state);
            startAudio(player, state);
        }
        return true;
    }

    public void stopAll() {
        for (UUID playerId : states.keySet().toArray(UUID[]::new)) {
            PlaybackState state = states.remove(playerId);
            if (state == null) {
                continue;
            }

            cancelSeekTransition(state);
            cancelTask(state);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restoreHiddenPlayers(player, state);
                clearFakeHelmet(player);
                restoreGameMode(player, state);
                actorPlaybackService.cleanup(player);
                subtitleLine1.remove(playerId);
                subtitleLine2.remove(playerId);
                stopAudio(player, state);
            }
        }
    }

    public void handleDisconnect(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        cancelTask(state);
        cancelSeekTransition(state);
        state.running = false;
        restoreHiddenPlayers(player, state);
        clearFakeHelmet(player);
        restoreGameMode(player, state);
        actorPlaybackService.cleanup(player);
        stopAudio(player, state);
    }

    public void handleJoin(Player player) {
        PlaybackState state = states.get(player.getUniqueId());
        if (state == null || state.running) {
            return;
        }

        subtitleLine1.put(player.getUniqueId(), "");
        subtitleLine2.put(player.getUniqueId(), "");
        startRunning(player, state);
        actorPlaybackService.start(player, state.cinematic, state.currentTick);
        startAudio(player, state);
    }

    private void startRunning(Player player, PlaybackState state) {
        hideOtherPlayersIfNeeded(player, state);
        applyFakePumpkin(player);
        applySpectatorMode(player, state);
        state.running = true;
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player), 0L, 1L);
    }

    private void hideOtherPlayersIfNeeded(Player viewer, PlaybackState state) {
        if (!state.cinematic.shouldHidePlayersDuringPlayback()) {
            return;
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }

            viewer.hidePlayer(plugin, target);
            state.hiddenPlayers.add(target.getUniqueId());
        }
    }

    private void restoreHiddenPlayers(Player viewer, PlaybackState state) {
        if (state.hiddenPlayers.isEmpty()) {
            return;
        }

        for (UUID hiddenPlayerId : state.hiddenPlayers) {
            Player target = Bukkit.getPlayer(hiddenPlayerId);
            if (target == null || !target.isOnline()) {
                continue;
            }

            viewer.showPlayer(plugin, target);
        }
        state.hiddenPlayers.clear();
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
                finishPlayback(player, state);
                return;
            }

            renderAtTick(player, state, state.currentTick, true);
            state.currentTick++;
        } catch (Exception ex) {
            plugin.getLogger().severe("Scene error for " + player.getName() + ": " + ex.getMessage());
            stop(player);
        }
    }

    private void renderAtTick(Player player, PlaybackState state, int tick, boolean executeTickCommands) {
        Location destination = interpolateLocation(state.cinematic, tick);
        if (destination == null || destination.getWorld() == null) {
            stop(player);
            return;
        }

        player.teleport(destination);
        if (executeTickCommands) {
            runTickCommands(player, state);
        }
        actorPlaybackService.tick(player, state.cinematic, tick);
        updateSubtitles(player, state);
    }

    private void runTickCommands(Player player, PlaybackState state) {
        List<String> commands = state.cinematic.getTickCommands().get(state.currentTick);
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (String rawCommand : commands) {
            String command = placeholderResolver.apply(rawCommand, player, state.cinematic, state.currentTick);
            if (command.isBlank()) {
                continue;
            }
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized);
        }
    }

    private void finishPlayback(Player player, PlaybackState state) {
        if (state.fullPlayback) {
            markAsPlayed(player.getUniqueId(), state.cinematic.getId());
        }

        runLifecycleCommands(player, state, state.cinematic.getEndCommands(), state.endTick);

        Cinematic.EndAction endAction = state.cinematic.getEndAction();
        if (endAction.type() == Cinematic.EndActionType.RETURN_TO_START) {
            if (state.startLocation != null && state.startLocation.getWorld() != null) {
                player.teleport(state.startLocation);
            }
        } else if (endAction.type() == Cinematic.EndActionType.TELEPORT) {
            Location teleportLocation = endAction.teleportLocation();
            if (teleportLocation != null && teleportLocation.getWorld() != null) {
                player.teleport(teleportLocation);
            }
        }

        GameMode configuredGameMode = endAction.gameMode();
        stop(player);
        if (configuredGameMode != null && player.isOnline()) {
            player.setGameMode(configuredGameMode);
        }
    }

    private void runLifecycleCommands(Player player, PlaybackState state, List<String> commands, int tick) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (String rawCommand : commands) {
            String command = placeholderResolver.apply(rawCommand, player, state.cinematic, tick);
            if (command.isBlank()) {
                continue;
            }
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized);
        }
    }

    private void applySpectatorMode(Player player, PlaybackState state) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        state.changedGameMode = true;
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void restoreGameMode(Player player, PlaybackState state) {
        if (!state.changedGameMode || state.originalGameMode == null) {
            return;
        }
        player.setGameMode(state.originalGameMode);
    }

    private Location interpolateLocation(Cinematic cinematic, int tick) {
        List<CinematicPoint> points = cinematic.getPoints();
        if (points.isEmpty()) {
            return null;
        }

        int nextIndex = 0;
        while (nextIndex < points.size() && points.get(nextIndex).tick() < tick) {
            nextIndex++;
        }

        CinematicPoint prev = nextIndex > 0 ? points.get(nextIndex - 1) : null;
        CinematicPoint next = nextIndex < points.size() ? points.get(nextIndex) : null;

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
        if (next.interpolationMode() == CinematicPoint.InterpolationMode.INSTANT) {
            return prev.location().clone();
        }
        if (prev.location().getWorld() == null || next.location().getWorld() == null) {
            return null;
        }
        if (!prev.location().getWorld().equals(next.location().getWorld())) {
            return prev.location().clone();
        }

        Location prevLocation = prev.location();
        Location nextLocation = next.location();
        double rawT = (tick - prev.tick()) / (double) (next.tick() - prev.tick());

        if (next.interpolationMode() == CinematicPoint.InterpolationMode.LINEAR) {
            double linearT = Math.max(0.0, Math.min(1.0, rawT));
            double x = lerp(prevLocation.getX(), nextLocation.getX(), linearT);
            double y = lerp(prevLocation.getY(), nextLocation.getY(), linearT);
            double z = lerp(prevLocation.getZ(), nextLocation.getZ(), linearT);
            float yaw = (float) lerpAngle(prevLocation.getYaw(), nextLocation.getYaw(), linearT);
            float pitch = (float) lerp(prevLocation.getPitch(), nextLocation.getPitch(), linearT);
            return new Location(prevLocation.getWorld(), x, y, z, yaw, pitch);
        }

        double smoothT = smootherStep(rawT);

        Location beforeLocation = nextIndex - 2 >= 0 ? points.get(nextIndex - 2).location() : prevLocation;
        Location afterLocation = nextIndex + 1 < points.size() ? points.get(nextIndex + 1).location() : nextLocation;

        double c1x = prevLocation.getX() + (nextLocation.getX() - beforeLocation.getX()) * (BEZIER_TENSION / 6.0);
        double c1y = prevLocation.getY() + (nextLocation.getY() - beforeLocation.getY()) * (BEZIER_TENSION / 6.0);
        double c1z = prevLocation.getZ() + (nextLocation.getZ() - beforeLocation.getZ()) * (BEZIER_TENSION / 6.0);

        double c2x = nextLocation.getX() - (afterLocation.getX() - prevLocation.getX()) * (BEZIER_TENSION / 6.0);
        double c2y = nextLocation.getY() - (afterLocation.getY() - prevLocation.getY()) * (BEZIER_TENSION / 6.0);
        double c2z = nextLocation.getZ() - (afterLocation.getZ() - prevLocation.getZ()) * (BEZIER_TENSION / 6.0);

        double x = cubicBezier(prevLocation.getX(), c1x, c2x, nextLocation.getX(), smoothT);
        double y = cubicBezier(prevLocation.getY(), c1y, c2y, nextLocation.getY(), smoothT);
        double z = cubicBezier(prevLocation.getZ(), c1z, c2z, nextLocation.getZ(), smoothT);
        float yaw = (float) lerpAngle(prevLocation.getYaw(), nextLocation.getYaw(), smoothT);
        float pitch = (float) lerp(prevLocation.getPitch(), nextLocation.getPitch(), smoothT);

        return new Location(prevLocation.getWorld(), x, y, z, yaw, pitch);
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double lerpAngle(double start, double end, double t) {
        double delta = ((end - start + 540.0) % 360.0) - 180.0;
        return start + delta * t;
    }

    private static double cubicBezier(double p0, double p1, double p2, double p3, double t) {
        double oneMinusT = 1.0 - t;
        double oneMinusTSquared = oneMinusT * oneMinusT;
        double tSquared = t * t;

        return oneMinusTSquared * oneMinusT * p0
                + 3.0 * oneMinusTSquared * t * p1
                + 3.0 * oneMinusT * tSquared * p2
                + tSquared * t * p3;
    }

    private static double smootherStep(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped * clamped * (clamped * (clamped * 6.0 - 15.0) + 10.0);
    }

    private void updateSubtitles(Player player, PlaybackState state) {
        CinematicSubtitleCue cue = state.cinematic.getSubtitleAtTick(state.currentTick);
        String line1 = cue == null ? "" : cue.line1();
        String line2 = cue == null ? "" : cue.line2();
        subtitleLine1.put(player.getUniqueId(), line1);
        subtitleLine2.put(player.getUniqueId(), line2);
    }

    private void startAudio(Player player, PlaybackState state) {
        CinematicAudioTrack track = state.cinematic.getAudioTrack();
        if (track == null || !track.isConfigured()) {
            return;
        }
        int playbackTick = Math.max(0, state.currentTick + 1);
        int seekMillis = Math.max(0, track.startAtMillis() + (playbackTick * 50));
        String payload = track.renderPlayCommand(player.getName(), seekMillis);
        openAudioCommandService.dispatch(payload, "playback-start scene=" + state.cinematic.getId() + " player=" + player.getName());
    }

    private void stopAudio(Player player, PlaybackState state) {
        CinematicAudioTrack track = state.cinematic.getAudioTrack();
        if (track == null || !track.isConfigured()) {
            return;
        }
        String stopCommand = track.renderStopCommand(player.getName());
        openAudioCommandService.dispatch(stopCommand, "playback-stop scene=" + state.cinematic.getId() + " player=" + player.getName());
    }

    private void markAsPlayed(UUID playerId, String cinematicId) {
        if (cinematicId == null || cinematicId.isBlank()) {
            return;
        }

        playedCinematics
                .computeIfAbsent(playerId, ignored -> new HashSet<>())
                .add(normalizeId(cinematicId));
    }

    private static String normalizeId(String cinematicId) {
        return cinematicId.toLowerCase(Locale.ROOT);
    }

    private static void cancelTask(PlaybackState state) {
        if (state.task != null) {
            state.task.cancel();
            state.task = null;
        }
    }

    private static void cancelSeekTransition(PlaybackState state) {
        if (state.seekTransitionTask != null) {
            state.seekTransitionTask.cancel();
            state.seekTransitionTask = null;
        }
    }

    private void startSeekTransition(Player player, PlaybackState state, int fromTick, int toTick, int steps) {
        if (steps <= 1) {
            state.currentTick = toTick;
            renderAtTick(player, state, toTick, false);
            return;
        }

        final int[] currentStep = {0};
        state.seekTransitionTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !states.containsKey(player.getUniqueId())) {
                cancelSeekTransition(state);
                return;
            }

            double t = currentStep[0] / (double) steps;
            double smoothT = smootherStep(t);
            int renderTick = (int) Math.round(fromTick + ((toTick - fromTick) * smoothT));
            renderTick = Math.max(0, Math.min(renderTick, state.endTick));

            state.currentTick = renderTick;
            renderAtTick(player, state, renderTick, false);

            currentStep[0]++;
            if (currentStep[0] > steps) {
                state.currentTick = toTick;
                renderAtTick(player, state, toTick, false);
                cancelSeekTransition(state);
            }
        }, 0L, 1L);
    }

    private void applyFakePumpkin(Player player) {
        applyMovementSpeedPenalty(player);
        player.sendEquipmentChange(player, EquipmentSlot.HEAD, createFakePumpkin());
    }

    private void clearFakeHelmet(Player player) {
        removeMovementSpeedPenalty(player);
        ItemStack realHelmet = player.getInventory().getHelmet();
        player.sendEquipmentChange(player, EquipmentSlot.HEAD, realHelmet);
    }

    private void applyMovementSpeedPenalty(Player player) {
        if (PUMPKIN_SPEED_PENALTY_KEY == null) {
            return;
        }

        AttributeInstance movementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        if (movementSpeed.getModifier(PUMPKIN_SPEED_PENALTY_KEY) != null) {
            return;
        }

        movementSpeed.addTransientModifier(new AttributeModifier(
                PUMPKIN_SPEED_PENALTY_KEY,
                -10.0,
                AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeMovementSpeedPenalty(Player player) {
        if (PUMPKIN_SPEED_PENALTY_KEY == null) {
            return;
        }

        AttributeInstance movementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        movementSpeed.removeModifier(PUMPKIN_SPEED_PENALTY_KEY);
    }

    private ItemStack createFakePumpkin() {
        ItemStack pumpkin = ItemStack.of(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        if (meta == null || PUMPKIN_SPEED_PENALTY_KEY == null) {
            return pumpkin;
        }

        AttributeModifier speedPenalty = new AttributeModifier(
                PUMPKIN_SPEED_PENALTY_KEY,
                -10.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.HEAD);
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, speedPenalty);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        pumpkin.setItemMeta(meta);
        return pumpkin;
    }

    private static final class PlaybackState {
        private final Cinematic cinematic;
        private final int endTick;
        private final boolean fullPlayback;
        private final Location startLocation;
        private final GameMode originalGameMode;
        private int currentTick;
        private boolean running;
        private boolean changedGameMode;
        private BukkitTask task;
        private BukkitTask seekTransitionTask;
        private final Set<UUID> hiddenPlayers = new HashSet<>();

        private PlaybackState(Cinematic cinematic, int startTick, int endTick, boolean fullPlayback, Location startLocation, GameMode originalGameMode) {
            this.cinematic = cinematic;
            this.currentTick = startTick;
            this.endTick = endTick;
            this.fullPlayback = fullPlayback;
            this.startLocation = startLocation == null ? null : startLocation.clone();
            this.originalGameMode = originalGameMode;
        }
    }
}
