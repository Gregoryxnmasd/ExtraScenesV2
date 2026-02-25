package com.extracraft.extrascenesv2;

import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import com.extracraft.extrascenesv2.commands.ExtraScenesCommand;
import com.extracraft.extrascenesv2.editor.TimelineEditorService;
import com.extracraft.extrascenesv2.listeners.ActorRecordingListener;
import com.extracraft.extrascenesv2.listeners.CinematicProtectionListener;
import com.extracraft.extrascenesv2.listeners.TimelineEditorListener;
import com.extracraft.extrascenesv2.placeholders.ExtraCraftSubtitleExpansion;
import com.extracraft.extrascenesv2.placeholders.ScenesPlaceholderExpansion;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraScenesV2Plugin extends JavaPlugin {

    private CinematicManager cinematicManager;
    private CinematicPlaybackService playbackService;
    private TimelineEditorService timelineEditorService;
    private org.bukkit.scheduler.BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib is required for packet-based scene actors. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.cinematicManager = new CinematicManager(this);
        this.cinematicManager.load();

        this.playbackService = new CinematicPlaybackService(this);
        this.timelineEditorService = new TimelineEditorService(cinematicManager, playbackService);

        registerCommands();
        new ScenesPlaceholderExpansion(this).register();
        new ExtraCraftSubtitleExpansion(this).register();
        getServer().getPluginManager().registerEvents(new CinematicProtectionListener(playbackService), this);
        getServer().getPluginManager().registerEvents(new TimelineEditorListener(timelineEditorService), this);

        this.autosaveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (cinematicManager != null) {
                cinematicManager.save();
            }
        }, 20L * 15L, 20L * 15L);

        getLogger().info("ExtraScenesV2 (Cinematics) enabled on " + getServer().getVersion());
    }

    @Override
    public void onDisable() {
        if (playbackService != null) {
            playbackService.stopAll();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (cinematicManager != null) {
            cinematicManager.save();
        }
    }

    public CinematicPlaybackService getPlaybackService() {
        return playbackService;
    }

    public CinematicManager getCinematicManager() {
        return cinematicManager;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("extrascenes");
        if (command == null) {
            getLogger().severe("Command 'extrascenes' is missing from plugin.yml");
            return;
        }

        ExtraScenesCommand executor = new ExtraScenesCommand(this, cinematicManager, playbackService, timelineEditorService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(new ActorRecordingListener(executor), this);
    }
}
