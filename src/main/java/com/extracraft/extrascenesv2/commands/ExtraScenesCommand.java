package com.extracraft.extrascenesv2.commands;

import com.extracraft.extrascenesv2.ExtraScenesV2Plugin;
import com.extracraft.extrascenesv2.network.BungeeMessenger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ExtraScenesCommand implements CommandExecutor, TabCompleter {

    private final ExtraScenesV2Plugin plugin;
    private final BungeeMessenger bungeeMessenger;

    public ExtraScenesCommand(ExtraScenesV2Plugin plugin, BungeeMessenger bungeeMessenger) {
        this.plugin = plugin;
        this.bungeeMessenger = bungeeMessenger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "ExtraScenesV2 " + ChatColor.GRAY + "- comandos:");
            sender.sendMessage(ChatColor.YELLOW + "/extrascenes info " + ChatColor.GRAY + "Muestra informacion basica");
            sender.sendMessage(ChatColor.YELLOW + "/extrascenes server " + ChatColor.GRAY + "Consulta el servidor bungee actual");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> {
                sender.sendMessage(ChatColor.GOLD + "Plugin: " + ChatColor.YELLOW + plugin.getDescription().getName());
                sender.sendMessage(ChatColor.GOLD + "Version: " + ChatColor.YELLOW + plugin.getDescription().getVersion());
                sender.sendMessage(ChatColor.GOLD + "Bungee: " + ChatColor.YELLOW + bungeeMessenger.getStatusLine());
                return true;
            }
            case "server" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Solo jugadores pueden ejecutar esta accion.");
                    return true;
                }
                bungeeMessenger.requestCurrentServer(player);
                sender.sendMessage(ChatColor.YELLOW + "Solicitando servidor actual al proxy...");
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Subcomando no reconocido.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("info");
            options.add("server");
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }
}
