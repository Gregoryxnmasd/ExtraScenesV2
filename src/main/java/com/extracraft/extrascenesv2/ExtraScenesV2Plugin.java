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
import java.io.File;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraScenesV2Plugin extends JavaPlugin {

    private CinematicManager cinematicManager;
    private CinematicPlaybackService playbackService;
    private TimelineEditorService timelineEditorService;
    private ExtraScenesCommand commandExecutor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureSkinsFolder();

        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib is required for packet-based scene actors. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.cinematicManager = new CinematicManager(this);
        this.cinematicManager.load();

        this.playbackService = new CinematicPlaybackService(this);
        boolean openAudioAvailable = getServer().getPluginManager().getPlugin("OpenAudioMc") != null
                || getServer().getPluginManager().getPlugin("OpenAudioMC") != null;
        if (!openAudioAvailable) {
            getLogger().warning("OpenAudioMC no detectado. La edición/reproducción seguirá funcionando sin audio OA.");
        }

        this.timelineEditorService = new TimelineEditorService(cinematicManager, playbackService);

        registerCommands();
        new ScenesPlaceholderExpansion(this).register();
        new ExtraCraftSubtitleExpansion(this).register();
        getServer().getPluginManager().registerEvents(new CinematicProtectionListener(playbackService), this);
        getServer().getPluginManager().registerEvents(new TimelineEditorListener(timelineEditorService), this);


        getLogger().info("ExtraScenesV2 (Cinematics) enabled on " + getServer().getVersion());
    }

    private void ensureSkinsFolder() {
        File skinsFolder = new File(getDataFolder(), "skins");
        if (!skinsFolder.exists() && !skinsFolder.mkdirs()) {
            getLogger().warning("No se pudo crear la carpeta de skins en " + skinsFolder.getAbsolutePath());
        }
    }

    @Override
    public void onDisable() {
        if (playbackService != null) {
            playbackService.stopAll();
        }
        if (commandExecutor != null) {
            commandExecutor.shutdown();
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

        this.commandExecutor = new ExtraScenesCommand(this, cinematicManager, playbackService, timelineEditorService);
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
        getServer().getPluginManager().registerEvents(new ActorRecordingListener(commandExecutor), this);
    }
}
