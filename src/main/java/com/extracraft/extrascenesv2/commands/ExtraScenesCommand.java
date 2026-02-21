package com.extracraft.extrascenesv2.commands;

import com.extracraft.extrascenesv2.cinematics.Cinematic;
import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import com.extracraft.extrascenesv2.cinematics.CinematicPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ExtraScenesCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "edit", "play", "stop", "record", "key", "tickcmd", "placeholders", "finish", "delete", "list", "show", "reload");

    private final JavaPlugin plugin;
    private final CinematicManager manager;
    private final CinematicPlaybackService playbackService;
    private final Map<UUID, RecordingState> recordings = new HashMap<>();

    public ExtraScenesCommand(JavaPlugin plugin, CinematicManager manager, CinematicPlaybackService playbackService) {
        this.plugin = plugin;
        this.manager = manager;
        this.playbackService = playbackService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCreate(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "play" -> handlePlay(sender, args);
            case "stop" -> handleStop(sender, args);
            case "record" -> handleRecord(sender, args);
            case "key" -> handleKey(sender, args);
            case "finish" -> handleFinish(sender, args);
            case "tickcmd" -> handleTickCommand(sender, args);
            case "placeholders" -> handlePlaceholders(sender);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "show" -> handleShow(sender, args);
            case "reload" -> {
                manager.load();
                sender.sendMessage(ChatColor.GREEN + "Escenas recargadas desde config.yml.");
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes create <scene> <durationTicks>");
            return;
        }

        int durationTicks;
        try {
            durationTicks = Math.max(1, Integer.parseInt(args[2]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "durationTicks debe ser entero > 0.");
            return;
        }

        if (!manager.createCinematic(args[1], durationTicks)) {
            sender.sendMessage(ChatColor.RED + "Ya existe una escena con ese ID.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Escena creada: " + args[1] + " (" + durationTicks + "t)");
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes edit <scene>");
            return;
        }

        Cinematic scene = manager.getCinematic(args[1]).orElse(null);
        if (scene == null) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Wizard de edición para '" + scene.getId() + "':");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key add " + scene.getId() + " <tick> here [smooth|instant]");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key set " + scene.getId() + " <tick> x y z yaw pitch [smooth|instant]");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key mode " + scene.getId() + " <tick> <smooth|instant>");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key del " + scene.getId() + " <tick>");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key list " + scene.getId() + " [page]");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes finish " + scene.getId() + " <return|stay|teleport_here|teleport>");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes tickcmd add " + scene.getId() + " <tick> <comando>");
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes play <scene> [startTick] [endTick]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar /scenes play.");
            return;
        }

        int startTick = 0;
        Integer endTick = null;

        if (args.length >= 3) {
            try {
                startTick = Math.max(0, Integer.parseInt(args[2]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "startTick inválido.");
                return;
            }
        }

        if (args.length >= 4) {
            try {
                endTick = Math.max(startTick, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "endTick inválido.");
                return;
            }
        }

        if (!playbackService.play(player, cinematic, startTick, endTick)) {
            sender.sendMessage(ChatColor.RED + "La escena no tiene keyframes.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Reproduciendo escena '" + cinematic.getId() + "'.");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar /scenes stop.");
            return;
        }

        if (!playbackService.stop(player)) {
            sender.sendMessage(ChatColor.RED + "No estás reproduciendo una escena.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Escena detenida.");
    }

    private void handleRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden grabar escenas.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes record <start|stop|clear>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> handleRecordStart(player, args);
            case "stop" -> handleRecordStop(player);
            case "clear" -> handleRecordClear(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Uso: /scenes record <start|stop|clear>");
        }
    }

    private void handleRecordStart(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Uso: /scenes record start <scene> [everyTicks] [duration:10s|200t]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            player.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        int everyTicks = 1;
        int maxTicks = cinematic.getDurationTicks();

        if (args.length >= 4) {
            try {
                everyTicks = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "everyTicks inválido.");
                return;
            }
        }

        if (args.length >= 5) {
            Integer parsed = parseDurationTicks(args[4]);
            if (parsed == null) {
                player.sendMessage(ChatColor.RED + "duration inválida. Usa 10s o 200t.");
                return;
            }
            maxTicks = parsed;
        }

        stopAndRemoveRecording(player.getUniqueId());

        RecordingState state = new RecordingState(args[2], everyTicks, maxTicks);
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player current = Bukkit.getPlayer(player.getUniqueId());
            if (current == null || !current.isOnline()) {
                stopAndRemoveRecording(player.getUniqueId());
                return;
            }

            if (state.currentTick > state.maxTicks) {
                stopAndRemoveRecording(player.getUniqueId());
                current.sendMessage(ChatColor.GREEN + "Grabación finalizada en " + state.currentTick + " ticks.");
                manager.save();
                return;
            }

            manager.upsertPoint(state.sceneId, state.currentTick, current.getLocation(), CinematicPoint.InterpolationMode.SMOOTH);
            state.currentTick += state.everyTicks;
        }, 0L, everyTicks);

        recordings.put(player.getUniqueId(), state);
        player.sendMessage(ChatColor.GREEN + "Grabación iniciada en escena '" + args[2] + "'.");
    }

    private void handleRecordStop(Player player) {
        if (!stopAndRemoveRecording(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "No hay grabación activa.");
            return;
        }

        manager.save();
        player.sendMessage(ChatColor.GREEN + "Grabación detenida.");
    }

    private void handleRecordClear(CommandSender sender, String[] args) {
        if (args.length < 5 || !"confirm".equalsIgnoreCase(args[4])) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes record clear <scene> confirm");
            return;
        }

        if (!manager.clearPoints(args[3])) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframes eliminados para " + args[3] + ".");
    }

    private void handleKey(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes key <add|set|mode|del|list|clear>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleKeyAdd(sender, args);
            case "set" -> handleKeySet(sender, args);
            case "mode" -> handleKeyMode(sender, args);
            case "del" -> handleKeyDel(sender, args);
            case "list" -> handleKeyList(sender, args);
            case "clear" -> handleKeyClear(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Uso: /scenes key <add|set|mode|del|list|clear>");
        }
    }

    private void handleKeyAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar 'here'.");
            return;
        }

        if (args.length < 5 || !"here".equalsIgnoreCase(args[4])) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes key add <scene> <tick> here [smooth|instant]");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "tick inválido.");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args.length >= 6 ? args[5] : null);
        if (interpolationMode == null) {
            sender.sendMessage(ChatColor.RED + "interpolation inválida. Usa smooth o instant.");
            return;
        }

        if (!manager.upsertPoint(args[2], tick, player.getLocation(), interpolationMode)) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframe guardado en tick " + tick + ".");
    }

    private void handleKeySet(CommandSender sender, String[] args) {
        if (args.length < 9) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes key set <scene> <tick> x y z yaw pitch [smooth|instant]");
            return;
        }

        int tick;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;

        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
            x = Double.parseDouble(args[4]);
            y = Double.parseDouble(args[5]);
            z = Double.parseDouble(args[6]);
            yaw = Float.parseFloat(args[7]);
            pitch = Float.parseFloat(args[8]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Valores numéricos inválidos.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar este comando (se usa tu mundo actual).");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args.length >= 10 ? args[9] : null);
        if (interpolationMode == null) {
            sender.sendMessage(ChatColor.RED + "interpolation inválida. Usa smooth o instant.");
            return;
        }

        Location location = new Location(player.getWorld(), x, y, z, yaw, pitch);
        if (!manager.upsertPoint(args[2], tick, location, interpolationMode)) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframe actualizado en tick " + tick + ".");
    }

    private void handleKeyDel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes key del <scene> <tick>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "tick inválido.");
            return;
        }

        if (!manager.deletePoint(args[2], tick)) {
            sender.sendMessage(ChatColor.RED + "No existe escena o no existe keyframe en ese tick.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframe eliminado en tick " + tick + ".");
    }

    private void handleKeyMode(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes key mode <scene> <tick> <smooth|instant>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "tick inválido.");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args[4]);
        if (interpolationMode == null) {
            sender.sendMessage(ChatColor.RED + "interpolation inválida. Usa smooth o instant.");
            return;
        }

        if (!manager.setPointInterpolation(args[2], tick, interpolationMode)) {
            sender.sendMessage(ChatColor.RED + "No existe escena o no existe keyframe en ese tick.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Interpolación actualizada en tick " + tick + " a "
            + interpolationMode.name().toLowerCase(Locale.ROOT) + ".");
    }

    private void handleKeyList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes key list <scene> [page]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        int page = 1;
        if (args.length >= 4) {
            try {
                page = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "page inválida.");
                return;
            }
        }

        int perPage = 8;
        List<CinematicPoint> points = cinematic.getPoints();
        if (points.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "La escena no tiene keyframes.");
            return;
        }

        int maxPages = (int) Math.ceil(points.size() / (double) perPage);
        int safePage = Math.min(page, maxPages);
        int from = (safePage - 1) * perPage;
        int to = Math.min(points.size(), from + perPage);

        sender.sendMessage(ChatColor.GOLD + "Keyframes de " + cinematic.getId() + " (página " + safePage + "/" + maxPages + "):");
        for (int i = from; i < to; i++) {
            CinematicPoint point = points.get(i);
            Location loc = point.location();
            sender.sendMessage(ChatColor.YELLOW + "t=" + point.tick() + ChatColor.GRAY + " ["
                + point.interpolationMode().name().toLowerCase(Locale.ROOT) + "] -> "
                + String.format(Locale.US, "%.2f %.2f %.2f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        }
    }

    private void handleKeyClear(CommandSender sender, String[] args) {
        if (args.length < 4 || !"confirm".equalsIgnoreCase(args[3])) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes key clear <scene> confirm");
            return;
        }

        if (!manager.clearPoints(args[2])) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Todos los keyframes fueron eliminados.");
    }


    private void handleTickCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes tickcmd <add|remove|list|clear> ...");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleTickCommandAdd(sender, args);
            case "remove" -> handleTickCommandRemove(sender, args);
            case "list" -> handleTickCommandList(sender, args);
            case "clear" -> handleTickCommandClear(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Uso: /scenes tickcmd <add|remove|list|clear> ...");
        }
    }

    private void handleTickCommandAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes tickcmd add <scene> <tick> <comando>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "tick inválido.");
            return;
        }

        String command = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        if (!manager.addTickCommand(args[2], tick, command)) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena o el comando es inválido.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Comando agregado en tick " + tick + ".");
    }

    private void handleTickCommandRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes tickcmd remove <scene> <tick> <indice>");
            return;
        }

        int tick;
        int index;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
            index = Integer.parseInt(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "tick o índice inválido.");
            return;
        }

        if (!manager.removeTickCommand(args[2], tick, index)) {
            sender.sendMessage(ChatColor.RED + "No se pudo eliminar. Revisa escena/tick/índice.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Comando #" + index + " eliminado en tick " + tick + ".");
    }

    private void handleTickCommandList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes tickcmd list <scene> [tick]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        if (args.length >= 4) {
            int tick;
            try {
                tick = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "tick inválido.");
                return;
            }

            List<String> commands = cinematic.getTickCommands().getOrDefault(tick, List.of());
            if (commands.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No hay comandos en el tick " + tick + ".");
                return;
            }

            sender.sendMessage(ChatColor.GOLD + "Comandos en tick " + tick + " (" + cinematic.getId() + "):");
            for (int i = 0; i < commands.size(); i++) {
                sender.sendMessage(ChatColor.YELLOW + "#" + (i + 1) + ChatColor.GRAY + " " + commands.get(i));
            }
            return;
        }

        if (cinematic.getTickCommands().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "La escena no tiene comandos por tick.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Ticks con comandos en '" + cinematic.getId() + "':");
        for (Map.Entry<Integer, List<String>> entry : cinematic.getTickCommands().entrySet()) {
            sender.sendMessage(ChatColor.YELLOW + "Tick " + entry.getKey() + ChatColor.GRAY + " -> " + entry.getValue().size() + " comando(s)");
        }
    }

    private void handleTickCommandClear(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes tickcmd clear <scene> <all|tick>");
            return;
        }

        Integer tick = null;
        if (!"all".equalsIgnoreCase(args[3])) {
            try {
                tick = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Usa all o un tick válido.");
                return;
            }
        }

        if (!manager.clearTickCommands(args[2], tick)) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + (tick == null
                ? "Todos los comandos por tick fueron eliminados."
                : "Comandos eliminados en tick " + tick + "."));
    }

    private void handlePlaceholders(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Placeholders disponibles:");
        sender.sendMessage(ChatColor.GRAY + "Compatibles con PlaceholderAPI (%placeholder%).");
        sender.sendMessage(ChatColor.YELLOW + "{player}" + ChatColor.GRAY + " nombre del jugador");
        sender.sendMessage(ChatColor.YELLOW + "{player_display_name}" + ChatColor.GRAY + " display name");
        sender.sendMessage(ChatColor.YELLOW + "{player_uuid}" + ChatColor.GRAY + " UUID");
        sender.sendMessage(ChatColor.YELLOW + "{scene}" + ChatColor.GRAY + " id de la escena");
        sender.sendMessage(ChatColor.YELLOW + "{tick}" + ChatColor.GRAY + " tick actual de reproducción");
        sender.sendMessage(ChatColor.YELLOW + "{world} {x} {y} {z} {yaw} {pitch}" + ChatColor.GRAY + " ubicación actual");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_played_<cinematic>_<player>%" + ChatColor.GRAY + " true/false si ese jugador ya vio la cinemática");
    }

    private void handleFinish(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes finish <scene> <return|stay|teleport_here|teleport>");
            return;
        }

        String sceneId = args[1];
        String mode = args[2].toLowerCase(Locale.ROOT);
        Cinematic.EndAction endAction;

        switch (mode) {
            case "return" -> endAction = Cinematic.EndAction.returnToStart();
            case "stay" -> endAction = Cinematic.EndAction.stayAtLastCameraPoint();
            case "teleport_here" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar teleport_here.");
                    return;
                }
                endAction = Cinematic.EndAction.teleportTo(player.getLocation());
            }
            case "teleport" -> {
                if (args.length < 9) {
                    sender.sendMessage(ChatColor.RED + "Uso: /scenes finish <scene> teleport <world> <x> <y> <z> <yaw> <pitch>");
                    return;
                }

                World world = Bukkit.getWorld(args[3]);
                if (world == null) {
                    sender.sendMessage(ChatColor.RED + "Mundo inválido: " + args[3]);
                    return;
                }

                double x;
                double y;
                double z;
                float yaw;
                float pitch;
                try {
                    x = Double.parseDouble(args[4]);
                    y = Double.parseDouble(args[5]);
                    z = Double.parseDouble(args[6]);
                    yaw = Float.parseFloat(args[7]);
                    pitch = Float.parseFloat(args[8]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Coordenadas inválidas.");
                    return;
                }

                endAction = Cinematic.EndAction.teleportTo(new Location(world, x, y, z, yaw, pitch));
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Modo inválido. Usa return, stay, teleport_here o teleport.");
                return;
            }
        }

        if (!manager.setEndAction(sceneId, endAction)) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Comportamiento final actualizado para '" + sceneId + "'.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes delete <scene>");
            return;
        }
        if (!manager.deleteCinematic(args[1])) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }
        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Escena eliminada: " + args[1]);
    }

    private void handleList(CommandSender sender) {
        List<String> ids = manager.getCinematicIds();
        if (ids.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay escenas creadas.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Escenas: " + ChatColor.YELLOW + String.join(", ", ids));
    }

    private void handleShow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /scenes show <scene>");
            return;
        }
        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Escena " + cinematic.getId() + ChatColor.GRAY + " -> duración " + cinematic.getDurationTicks() + "t, " + cinematic.getPoints().size() + " keyframes");
        sender.sendMessage(ChatColor.GRAY + "Final: " + describeEndAction(cinematic.getEndAction()));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "ExtraScenesV2 - comandos /" + label + ":");
        sender.sendMessage(ChatColor.YELLOW + "/scenes create <scene> <durationTicks>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes edit <scene>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes play <scene> [startTick] [endTick]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes stop");
        sender.sendMessage(ChatColor.YELLOW + "/scenes record start <scene> [everyTicks] [duration:10s|200t]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes record stop");
        sender.sendMessage(ChatColor.YELLOW + "/scenes record clear <scene> confirm");
        sender.sendMessage(ChatColor.YELLOW + "/scenes key add <scene> <tick> here [smooth|instant]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes key set <scene> <tick> x y z yaw pitch [smooth|instant]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes key mode <scene> <tick> <smooth|instant>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes key del <scene> <tick>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes key list <scene> [page]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes key clear <scene> confirm");
        sender.sendMessage(ChatColor.YELLOW + "/scenes finish <scene> <return|stay|teleport_here|teleport>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes tickcmd <add|remove|list|clear> ...");
        sender.sendMessage(ChatColor.YELLOW + "/scenes placeholders");
    }

    private boolean stopAndRemoveRecording(UUID playerId) {
        RecordingState state = recordings.remove(playerId);
        if (state == null) {
            return false;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        return true;
    }

    private Integer parseDurationTicks(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("t")) {
                return Math.max(1, Integer.parseInt(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("s")) {
                int seconds = Math.max(1, Integer.parseInt(value.substring(0, value.length() - 1)));
                return seconds * 20;
            }
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private CinematicPoint.InterpolationMode parseInterpolationMode(String raw) {
        if (raw == null) {
            return CinematicPoint.InterpolationMode.SMOOTH;
        }

        if ("smooth".equalsIgnoreCase(raw)) {
            return CinematicPoint.InterpolationMode.SMOOTH;
        }

        if ("instant".equalsIgnoreCase(raw)) {
            return CinematicPoint.InterpolationMode.INSTANT;
        }

        return null;
    }


    private String describeEndAction(Cinematic.EndAction endAction) {
        if (endAction.type() == Cinematic.EndActionType.RETURN_TO_START) {
            return "volver al punto inicial";
        }

        if (endAction.type() == Cinematic.EndActionType.TELEPORT) {
            Location target = endAction.teleportLocation();
            if (target == null || target.getWorld() == null) {
                return "teleport sin destino (usa /scenes finish para configurarlo)";
            }

            return String.format(Locale.US,
                    "teleport a %s %.2f %.2f %.2f %.1f %.1f",
                    target.getWorld().getName(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getYaw(),
                    target.getPitch());
        }

        return "quedarse en el último punto de cámara";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }

        if (args.length == 2 && Arrays.asList("edit", "play", "delete", "show", "finish").contains(args[0].toLowerCase(Locale.ROOT))) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[1])).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("record")) {
            return List.of("start", "stop", "clear");
        }

        if (args.length == 3 && Arrays.asList("play", "edit", "delete", "show").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.emptyList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("finish")) {
            return List.of("return", "stay", "teleport_here", "teleport");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("finish") && args[2].equalsIgnoreCase("teleport")) {
            return Bukkit.getWorlds().stream().map(World::getName).filter(s -> s.startsWith(args[3])).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("record") && Arrays.asList("start", "clear").contains(args[1].toLowerCase(Locale.ROOT))) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("key")) {
            return List.of("add", "set", "mode", "del", "list", "clear");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("key")) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("add")) {
            return List.of("here");
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("add")) {
            return List.of("smooth", "instant");
        }

        if (args.length == 10 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("set")) {
            return List.of("smooth", "instant");
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("mode")) {
            return List.of("smooth", "instant");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("clear")) {
            return List.of("confirm");
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("record") && args[1].equalsIgnoreCase("clear")) {
            return List.of("confirm");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("tickcmd")) {
            return List.of("add", "remove", "list", "clear");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("tickcmd")) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("tickcmd") && args[1].equalsIgnoreCase("clear")) {
            return List.of("all");
        }

        return new ArrayList<>();
    }

    private static final class RecordingState {
        private final String sceneId;
        private final int everyTicks;
        private final int maxTicks;
        private int currentTick;
        private BukkitTask task;

        private RecordingState(String sceneId, int everyTicks, int maxTicks) {
            this.sceneId = sceneId;
            this.everyTicks = everyTicks;
            this.maxTicks = Math.max(1, maxTicks);
        }
    }
}
