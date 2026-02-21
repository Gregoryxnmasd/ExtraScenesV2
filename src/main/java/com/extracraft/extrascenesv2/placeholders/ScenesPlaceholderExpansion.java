package com.extracraft.extrascenesv2.placeholders;

import com.extracraft.extrascenesv2.ExtraScenesV2Plugin;
import java.util.Locale;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScenesPlaceholderExpansion extends PlaceholderExpansion {

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
        if (!params.toLowerCase(Locale.ROOT).startsWith("played_")) {
            return null;
        }

        String rawArgs = params.substring("played_".length());
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
