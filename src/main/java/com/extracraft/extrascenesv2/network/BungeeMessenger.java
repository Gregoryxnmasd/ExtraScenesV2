package com.extracraft.extrascenesv2.network;

import com.extracraft.extrascenesv2.ExtraScenesV2Plugin;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class BungeeMessenger implements PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";

    private final ExtraScenesV2Plugin plugin;
    private boolean enabled;

    public BungeeMessenger(ExtraScenesV2Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("network.bungee.enabled", true);
    }

    public void register() {
        if (!enabled) {
            plugin.getLogger().info("Bungee messaging disabled in config.");
            return;
        }

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getLogger().info("Bungee messaging channel registered.");
    }

    public void unregister() {
        if (!enabled) {
            return;
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void requestCurrentServer(Player player) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Bungee messaging esta desactivado.");
            return;
        }

        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("GetServer");
        player.sendPluginMessage(plugin, CHANNEL, output.toByteArray());
    }

    public String getStatusLine() {
        return enabled ? "activo" : "desactivado";
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || !enabled) {
            return;
        }

        ByteArrayDataInput input = ByteStreams.newDataInput(message);
        String subchannel = input.readUTF();
        if ("GetServer".equalsIgnoreCase(subchannel)) {
            String serverName = input.readUTF();
            player.sendMessage(ChatColor.GREEN + "Estas conectado a: " + ChatColor.YELLOW + serverName);
        }
    }
}
