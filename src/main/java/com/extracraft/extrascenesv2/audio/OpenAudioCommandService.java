package com.extracraft.extrascenesv2.audio;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpenAudioCommandService {

    private final JavaPlugin plugin;

    public OpenAudioCommandService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return resolveOpenAudioPlugin() != null;
    }

    public boolean dispatch(String command, String context) {
        if (command == null || command.isBlank()) {
            return false;
        }

        Plugin oaPlugin = resolveOpenAudioPlugin();
        if (oaPlugin == null) {
            plugin.getLogger().fine("OpenAudioMC command skipped (plugin missing): " + context);
            return false;
        }

        String normalized = command.startsWith("/") ? command.substring(1) : command;
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized);
        } catch (Exception ex) {
            plugin.getLogger().warning("OpenAudioMC command failed (" + context + "): " + ex.getMessage());
            return false;
        }
    }

    private Plugin resolveOpenAudioPlugin() {
        Plugin openAudioMc = plugin.getServer().getPluginManager().getPlugin("OpenAudioMc");
        if (openAudioMc != null && openAudioMc.isEnabled()) {
            return openAudioMc;
        }

        Plugin openAudioMC = plugin.getServer().getPluginManager().getPlugin("OpenAudioMC");
        if (openAudioMC != null && openAudioMC.isEnabled()) {
            return openAudioMC;
        }

        return null;
    }
}
