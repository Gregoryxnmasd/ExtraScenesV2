package com.extrascenesv2;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraScenesPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        PluginCommand sceneCommand = getCommand("scene");
        if (sceneCommand == null) {
            getLogger().severe("Command /scene is not defined in plugin.yml");
            return;
        }

        SceneCommandExecutor executor = new SceneCommandExecutor();
        sceneCommand.setExecutor(executor);
        sceneCommand.setTabCompleter(executor);

        getLogger().info("ExtraScenesV2 enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ExtraScenesV2 disabled.");
    }
}
