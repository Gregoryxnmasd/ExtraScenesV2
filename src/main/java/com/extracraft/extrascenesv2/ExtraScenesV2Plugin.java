package com.extracraft.extrascenesv2;

import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import com.extracraft.extrascenesv2.commands.ExtraScenesCommand;
import com.extracraft.extrascenesv2.listeners.CinematicProtectionListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraScenesV2Plugin extends JavaPlugin {

    private CinematicManager cinematicManager;
    private CinematicPlaybackService playbackService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.cinematicManager = new CinematicManager(this);
        this.cinematicManager.load();

        this.playbackService = new CinematicPlaybackService(this);

        registerCommands();
        getServer().getPluginManager().registerEvents(new CinematicProtectionListener(playbackService), this);

        getLogger().info("ExtraScenesV2 (Cinematics) enabled on " + getServer().getVersion());
    }

    @Override
    public void onDisable() {
        if (playbackService != null) {
            playbackService.stopAll();
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("extrascenes");
        if (command == null) {
            getLogger().severe("Command 'extrascenes' is missing from plugin.yml");
            return;
        }

        ExtraScenesCommand executor = new ExtraScenesCommand(cinematicManager, playbackService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
