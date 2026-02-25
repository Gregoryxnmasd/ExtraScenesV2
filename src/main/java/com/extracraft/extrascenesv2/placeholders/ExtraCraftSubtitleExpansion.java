package com.extracraft.extrascenesv2.placeholders;

import com.extracraft.extrascenesv2.ExtraScenesV2Plugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExtraCraftSubtitleExpansion extends PlaceholderExpansion {

    private final ExtraScenesV2Plugin plugin;

    public ExtraCraftSubtitleExpansion(ExtraScenesV2Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "extracraft";
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
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        if (params.equalsIgnoreCase("sub_1")) {
            return plugin.getPlaybackService().getSubtitleLine(player.getUniqueId(), 1);
        }

        if (params.equalsIgnoreCase("sub_2")) {
            return plugin.getPlaybackService().getSubtitleLine(player.getUniqueId(), 2);
        }

        return null;
    }
}
