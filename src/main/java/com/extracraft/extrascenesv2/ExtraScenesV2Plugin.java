package com.extracraft.extrascenesv2;

import com.extracraft.extrascenesv2.commands.ExtraScenesCommand;
import com.extracraft.extrascenesv2.network.BungeeMessenger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraScenesV2Plugin extends JavaPlugin {

    private BungeeMessenger bungeeMessenger;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.bungeeMessenger = new BungeeMessenger(this);
        this.bungeeMessenger.register();

        registerCommands();

        getLogger().info("ExtraScenesV2 enabled on " + getServer().getVersion());
    }

    @Override
    public void onDisable() {
        if (bungeeMessenger != null) {
            bungeeMessenger.unregister();
        }
    }

    private void registerCommands() {
        final PluginCommand command = getCommand("extrascenes");
        if (command == null) {
            getLogger().severe("Command 'extrascenes' is missing from plugin.yml");
            return;
        }

        final ExtraScenesCommand executor = new ExtraScenesCommand(this, bungeeMessenger);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public BungeeMessenger getBungeeMessenger() {
        return bungeeMessenger;
    }
}
