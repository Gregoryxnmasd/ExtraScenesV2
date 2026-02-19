package com.extracraft.extrascenesv2.commands;

import com.extracraft.extrascenesv2.cinematics.Cinematic;
import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import com.extracraft.extrascenesv2.cinematics.CinematicPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ExtraScenesCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "delete", "addpoint", "list", "show", "play", "stop", "reload");

    private final CinematicManager manager;
    private final CinematicPlaybackService playbackService;

    public ExtraScenesCommand(CinematicManager manager, CinematicPlaybackService playbackService) {
        this.manager = manager;
        this.playbackService = playbackService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "addpoint" -> handleAddPoint(sender, args);
            case "list" -> handleList(sender);
            case "show" -> handleShow(sender, args);
            case "play" -> handlePlay(sender, args);
            case "stop" -> handleStop(sender, args);
            case "reload" -> {
                manager.load();
                sender.sendMessage(ChatColor.GREEN + "Cinematics recargadas desde config.yml.");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /extrascenes create <id>");
            return;
        }
        if (!manager.createCinematic(args[1])) {
            sender.sendMessage(ChatColor.RED + "Ya existe una cinematica con ese ID.");
            return;
        }
        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Cinematica creada: " + args[1]);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /extrascenes delete <id>");
            return;
        }
        if (!manager.deleteCinematic(args[1])) {
            sender.sendMessage(ChatColor.RED + "No existe esa cinematica.");
            return;
        }
        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Cinematica eliminada: " + args[1]);
    }

    private void handleAddPoint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden agregar puntos.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /extrascenes addpoint <id> <locked|free> <duracionTicks>");
            return;
        }

        CinematicPoint.CameraMode mode;
        try {
            mode = CinematicPoint.CameraMode.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Modo invalido. Usa: locked o free.");
            return;
        }

        int duration;
        try {
            duration = Math.max(1, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "La duracion debe ser un numero entero > 0.");
            return;
        }

        CinematicPoint point = new CinematicPoint(player.getLocation().clone(), mode, duration);
        if (!manager.addPoint(args[1], point)) {
            sender.sendMessage(ChatColor.RED + "No existe la cinematica " + args[1]);
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Punto agregado a " + args[1] + " (" + mode.name().toLowerCase(Locale.ROOT) + ").");
    }

    private void handleList(CommandSender sender) {
        List<String> ids = manager.getCinematicIds();
        if (ids.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay cinematicas creadas.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Cinematicas: " + ChatColor.YELLOW + String.join(", ", ids));
    }

    private void handleShow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /extrascenes show <id>");
            return;
        }
        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "No existe esa cinematica.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Cinematica " + cinematic.getId() + ChatColor.GRAY + " -> " + cinematic.getPoints().size() + " puntos");
        for (int i = 0; i < cinematic.getPoints().size(); i++) {
            CinematicPoint point = cinematic.getPoints().get(i);
            sender.sendMessage(ChatColor.YELLOW + "#" + (i + 1) + ChatColor.GRAY + " "
                + point.cameraMode().name().toLowerCase(Locale.ROOT)
                + " | " + point.durationTicks() + "t"
                + " | " + point.location().getWorld().getName()
                + " " + String.format(Locale.US, "%.2f %.2f %.2f", point.location().getX(), point.location().getY(), point.location().getZ()));
        }
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /extrascenes play <id> [jugador]");
            return;
        }
        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "No existe esa cinematica.");
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "Desde consola debes especificar jugador.");
            return;
        }

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado.");
            return;
        }

        if (!playbackService.play(target, cinematic)) {
            sender.sendMessage(ChatColor.RED + "La cinematica no tiene puntos.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Reproduciendo cinematica '" + cinematic.getId() + "' para " + target.getName());
    }

    private void handleStop(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "Desde consola debes especificar jugador.");
            return;
        }

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado.");
            return;
        }

        if (!playbackService.stop(target)) {
            sender.sendMessage(ChatColor.RED + "Ese jugador no esta en una cinematica.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Cinematica detenida para " + target.getName());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "ExtraScenesV2 - comandos:");
        sender.sendMessage(ChatColor.YELLOW + "/extrascenes create <id>");
        sender.sendMessage(ChatColor.YELLOW + "/extrascenes addpoint <id> <locked|free> <duracionTicks>");
        sender.sendMessage(ChatColor.YELLOW + "/extrascenes play <id> [jugador]");
        sender.sendMessage(ChatColor.YELLOW + "/extrascenes stop [jugador]");
        sender.sendMessage(ChatColor.YELLOW + "/extrascenes show <id>, /extrascenes list, /extrascenes delete <id>, /extrascenes reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && Arrays.asList("delete", "addpoint", "show", "play").contains(args[0].toLowerCase(Locale.ROOT))) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addpoint")) {
            return List.of("locked", "free");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            return null;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            return null;
        }
        return Collections.emptyList();
    }
}
