package com.extracraft.extrascenesv2.placeholders;

import com.extracraft.extrascenesv2.ExtraScenesV2Plugin;
import com.extracraft.extrascenesv2.cinematics.Cinematic;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScenesPlaceholderExpansion extends PlaceholderExpansion {

    private static final String NONE_VALUE = "none";

    private final ExtraScenesV2Plugin plugin;

    public ScenesPlaceholderExpansion(ExtraScenesV2Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "scenes";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer requestPlayer, @NotNull String params) {
        String lowered = params.toLowerCase(Locale.ROOT);

        if ("scenes_count".equals(lowered)) {
            return String.valueOf(plugin.getCinematicManager().getCinematicIds().size());
        }

        if ("scenes_list".equals(lowered)) {
            String ids = String.join(",", plugin.getCinematicManager().getCinematicIds());
            return ids.isBlank() ? NONE_VALUE : ids;
        }

        if (lowered.startsWith("played_")) {
            return resolvePlayed(requestPlayer, params.substring("played_".length()));
        }

        if (lowered.equals("playing") || lowered.startsWith("playing_")) {
            UUID playerId = resolveTargetFromSuffix(requestPlayer, params, "playing");
            Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
            return String.valueOf(player != null && plugin.getPlaybackService().isInCinematic(player));
        }

        if (lowered.equals("current_scene") || lowered.startsWith("current_scene_")) {
            UUID playerId = resolveTargetFromSuffix(requestPlayer, params, "current_scene");
            if (playerId == null) {
                return NONE_VALUE;
            }

            String currentScene = plugin.getPlaybackService().getCurrentSceneId(playerId);
            return currentScene.isBlank() ? NONE_VALUE : currentScene;
        }

        if (lowered.equals("current_tick") || lowered.startsWith("current_tick_")) {
            UUID playerId = resolveTargetFromSuffix(requestPlayer, params, "current_tick");
            return String.valueOf(plugin.getPlaybackService().getCurrentTick(playerId));
        }

        if (lowered.equals("end_tick") || lowered.startsWith("end_tick_")) {
            UUID playerId = resolveTargetFromSuffix(requestPlayer, params, "end_tick");
            return String.valueOf(plugin.getPlaybackService().getCurrentEndTick(playerId));
        }

        if (lowered.equals("progress_percent") || lowered.startsWith("progress_percent_")) {
            UUID playerId = resolveTargetFromSuffix(requestPlayer, params, "progress_percent");
            int currentTick = plugin.getPlaybackService().getCurrentTick(playerId);
            int endTick = plugin.getPlaybackService().getCurrentEndTick(playerId);
            if (endTick <= 0) {
                return "0";
            }

            int percent = (int) Math.round((currentTick * 100.0) / endTick);
            return String.valueOf(Math.max(0, Math.min(percent, 100)));
        }

        if (lowered.equals("played_count") || lowered.startsWith("played_count_")) {
            UUID playerId = resolveTargetFromSuffix(requestPlayer, params, "played_count");
            return String.valueOf(plugin.getPlaybackService().getPlayedCount(playerId));
        }

        if (lowered.equals("played_list") || lowered.startsWith("played_list_")) {
            UUID playerId = resolveTargetFromSuffix(requestPlayer, params, "played_list");
            Set<String> sceneIds = plugin.getPlaybackService().getPlayedSceneIds(playerId);
            if (sceneIds.isEmpty()) {
                return NONE_VALUE;
            }

            return sceneIds.stream()
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining(","));
        }

        if (lowered.startsWith("duration_")) {
            String sceneId = params.substring("duration_".length());
            Cinematic scene = plugin.getCinematicManager().getCinematic(sceneId).orElse(null);
            return scene == null ? "0" : String.valueOf(scene.getDurationTicks());
        }

        if (lowered.startsWith("points_")) {
            String sceneId = params.substring("points_".length());
            Cinematic scene = plugin.getCinematicManager().getCinematic(sceneId).orElse(null);
            return scene == null ? "0" : String.valueOf(scene.getPoints().size());
        }

        if (lowered.startsWith("commands_")) {
            String sceneId = params.substring("commands_".length());
            Cinematic scene = plugin.getCinematicManager().getCinematic(sceneId).orElse(null);
            if (scene == null) {
                return "0";
            }

            int totalCommands = scene.getTickCommands().values().stream()
                    .mapToInt(java.util.List::size)
                    .sum();
            return String.valueOf(totalCommands);
        }

        return null;
    }

    private String resolvePlayed(OfflinePlayer requestPlayer, String rawArgs) {
        if (rawArgs.isBlank()) {
            return "false";
        }

        String[] parts = rawArgs.split("_");
        if (parts.length == 0) {
            return "false";
        }

        UUID targetPlayerId = requestPlayer == null ? null : requestPlayer.getUniqueId();
        String cinematicId = rawArgs;

        if (parts.length >= 2) {
            String playerToken = parts[parts.length - 1];
            UUID resolved = resolvePlayerId(playerToken);
            if (resolved != null) {
                targetPlayerId = resolved;
                cinematicId = rawArgs.substring(0, rawArgs.length() - playerToken.length() - 1);
            }
        }

        if (targetPlayerId == null || cinematicId.isBlank()) {
            return "false";
        }

        return String.valueOf(plugin.getPlaybackService().hasPlayerPlayed(cinematicId, targetPlayerId));
    }

    private UUID resolveTargetFromSuffix(OfflinePlayer requestPlayer, String rawParam, String rootPlaceholder) {
        String suffix = rawParam.substring(rootPlaceholder.length());
        if (suffix.isBlank()) {
            return requestPlayer == null ? null : requestPlayer.getUniqueId();
        }

        if (!suffix.startsWith("_")) {
            return requestPlayer == null ? null : requestPlayer.getUniqueId();
        }

        String playerToken = suffix.substring(1);
        if (playerToken.isBlank()) {
            return requestPlayer == null ? null : requestPlayer.getUniqueId();
        }

        UUID resolved = resolvePlayerId(playerToken);
        if (resolved != null) {
            return resolved;
        }

        return requestPlayer == null ? null : requestPlayer.getUniqueId();
    }

    private UUID resolvePlayerId(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }

        Player onlinePlayer = Bukkit.getPlayerExact(token);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(token);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return offlinePlayer.getUniqueId();
        }

        return null;
    }
}
