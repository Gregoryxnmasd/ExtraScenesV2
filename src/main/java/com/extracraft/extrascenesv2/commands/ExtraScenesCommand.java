package com.extracraft.extrascenesv2.commands;

import com.extracraft.extrascenesv2.cinematics.Cinematic;
import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import com.extracraft.extrascenesv2.cinematics.CinematicPoint;
import com.extracraft.extrascenesv2.cinematics.SceneActor;
import com.extracraft.extrascenesv2.cinematics.ActorFrame;
import com.extracraft.extrascenesv2.cinematics.ActorPlaybackService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
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

    private static final List<String> SUBCOMMANDS = List.of("create", "edit", "play", "stop", "record", "actor", "key", "tickcmd", "placeholders", "finish", "delete", "list", "show", "reload");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern UUID_ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([a-fA-F0-9]{32})\"");
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final CinematicManager manager;
    private final CinematicPlaybackService playbackService;
    private final Map<UUID, RecordingState> recordings = new HashMap<>();
    private final Map<UUID, ActorRecordingState> actorRecordings = new HashMap<>();
    private final ActorPlaybackService actorPreviewService;

    public ExtraScenesCommand(JavaPlugin plugin, CinematicManager manager, CinematicPlaybackService playbackService) {
        this.plugin = plugin;
        this.manager = manager;
        this.playbackService = playbackService;
        this.actorPreviewService = new ActorPlaybackService(plugin);
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
            case "actor" -> handleActor(sender, args);
            case "key" -> handleKey(sender, args);
            case "finish" -> handleFinish(sender, args);
            case "tickcmd" -> handleTickCommand(sender, args);
            case "placeholders" -> handlePlaceholders(sender);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "show" -> handleShow(sender, args);
            case "reload" -> {
                manager.load();
                sender.sendMessage(ChatColor.GREEN + "Scenes reloaded from config.yml.");
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes create <scene> <durationTicks>");
            return;
        }

        int durationTicks;
        try {
            durationTicks = Math.max(1, Integer.parseInt(args[2]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "durationTicks must be an integer > 0.");
            return;
        }

        if (!manager.createCinematic(args[1], durationTicks)) {
            sender.sendMessage(ChatColor.RED + "A scene with that ID already exists.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Scene created: " + args[1] + " (" + durationTicks + "t)");
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes edit <scene>");
            return;
        }

        Cinematic scene = manager.getCinematic(args[1]).orElse(null);
        if (scene == null) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Edit wizard for '" + scene.getId() + "':");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key add " + scene.getId() + " <tick> here [smooth|instant]");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key set " + scene.getId() + " <tick> x y z yaw pitch [smooth|instant]");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key mode " + scene.getId() + " <tick> <smooth|instant>");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key del " + scene.getId() + " <tick>");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes key list " + scene.getId() + " [page]");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes finish " + scene.getId() + " <return|stay|teleport_here|teleport>");
        sender.sendMessage(ChatColor.YELLOW + "- /scenes tickcmd add " + scene.getId() + " <tick> <command>");
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes play <scene> [startTick] [endTick]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /scenes play.");
            return;
        }

        int startTick = 0;
        Integer endTick = null;

        if (args.length >= 3) {
            try {
                startTick = Math.max(0, Integer.parseInt(args[2]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid startTick.");
                return;
            }
        }

        if (args.length >= 4) {
            try {
                endTick = Math.max(startTick, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid endTick.");
                return;
            }
        }

        if (!playbackService.play(player, cinematic, startTick, endTick)) {
            sender.sendMessage(ChatColor.RED + "This scene has no keyframes.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Playing scene '" + cinematic.getId() + "'.");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /scenes stop.");
            return;
        }

        if (!playbackService.stop(player)) {
            sender.sendMessage(ChatColor.RED + "You are not playing any scene.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Scene stopped.");
    }

    private void handleRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can record scenes.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes record <start|stop|clear>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> handleRecordStart(player, args);
            case "stop" -> handleRecordStop(player);
            case "clear" -> handleRecordClear(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Usage: /scenes record <start|stop|clear>");
        }
    }

    private void handleRecordStart(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /scenes record start <scene> [everyTicks] [duration:10s|200t]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            player.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        int everyTicks = 1;
        int maxTicks = cinematic.getDurationTicks();

        if (args.length >= 4) {
            try {
                everyTicks = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Invalid everyTicks.");
                return;
            }
        }

        if (args.length >= 5) {
            Integer parsed = parseDurationTicks(args[4]);
            if (parsed == null) {
                player.sendMessage(ChatColor.RED + "Invalid duration. Use 10s or 200t.");
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
                current.sendMessage(ChatColor.GREEN + "Recording finished at " + state.currentTick + " ticks.");
                manager.save();
                return;
            }

            manager.upsertPoint(state.sceneId, state.currentTick, current.getLocation(), CinematicPoint.InterpolationMode.SMOOTH);
            state.currentTick += state.everyTicks;
        }, 0L, everyTicks);

        recordings.put(player.getUniqueId(), state);
        player.sendMessage(ChatColor.GREEN + "Recording started on scene '" + args[2] + "'.");
    }

    private void handleRecordStop(Player player) {
        if (!stopAndRemoveRecording(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "There is no active recording.");
            return;
        }

        manager.save();
        player.sendMessage(ChatColor.GREEN + "Recording stopped.");
    }

    private void handleRecordClear(CommandSender sender, String[] args) {
        if (args.length < 5 || !"confirm".equalsIgnoreCase(args[4])) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes record clear <scene> confirm");
            return;
        }

        if (!manager.clearPoints(args[3])) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframes removed for " + args[3] + ".");
    }

    private void handleActor(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes actor <create|skin|window|record>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> handleActorCreate(sender, args);
            case "skin" -> handleActorSkin(sender, args);
            case "window" -> handleActorWindow(sender, args);
            case "record" -> handleActorRecord(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Usage: /scenes actor <create|skin|window|record>");
        }
    }

    private void handleActorCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes actor create <scene> <actorId> [scale]");
            return;
        }

        Double scale = 1.0D;
        if (args.length >= 5) {
            try {
                scale = Double.parseDouble(args[4]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Escala inválida.");
                return;
            }
        }

        if (!manager.upsertActor(args[2], args[3], args[3], scale, null, null)) {
            sender.sendMessage(ChatColor.RED + "No existe esa escena.");
            return;
        }
        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Actor guardado.");
    }

    private void handleActorSkin(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes actor skin <scene> <actorId> <playerName|texture> [signature]");
            return;
        }

        SceneActor actor = manager.getActor(args[2], args[3]);
        if (actor == null) {
            sender.sendMessage(ChatColor.RED + "Actor no existe. Créalo primero.");
            return;
        }

        SkinData skinData;
        if (args.length >= 6) {
            skinData = new SkinData(args[4], args[5]);
        } else {
            skinData = fetchSkinByName(args[4]);
            if (skinData == null) {
                sender.sendMessage(ChatColor.RED + "No se pudo resolver la skin premium de ese usuario.");
                sender.sendMessage(ChatColor.GRAY + "También puedes usar texture+signature manualmente.");
                return;
            }
        }

        if (!manager.upsertActor(args[2], args[3], actor.displayName(), actor.scale(), skinData.texture(), skinData.signature())) {
            sender.sendMessage(ChatColor.RED + "No se pudo actualizar skin.");
            return;
        }
        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Skin del actor guardada.");
    }

    private SkinData fetchSkinByName(String username) {
        String cleanName = username == null ? "" : username.trim();
        if (cleanName.isEmpty()) {
            return null;
        }

        String uuid = fetchMojangUuid(cleanName);
        if (uuid == null) {
            return null;
        }

        return fetchSessionSkin(uuid);
    }

    private String fetchMojangUuid(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            Matcher matcher = UUID_ID_PATTERN.matcher(response.body());
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private SkinData fetchSessionSkin(String uuid) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            Matcher textureMatcher = TEXTURE_PATTERN.matcher(response.body());
            Matcher signatureMatcher = SIGNATURE_PATTERN.matcher(response.body());
            if (!textureMatcher.find() || !signatureMatcher.find()) {
                return null;
            }

            return new SkinData(textureMatcher.group(1), signatureMatcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }


    private void handleActorWindow(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes actor window <scene> <actorId> <appearTick> <disappearTick>");
            return;
        }

        int appearTick;
        int disappearTick;
        try {
            appearTick = Math.max(0, Integer.parseInt(args[4]));
            disappearTick = Math.max(appearTick, Integer.parseInt(args[5]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Ticks inválidos.");
            return;
        }

        if (!manager.setActorWindow(args[2], args[3], appearTick, disappearTick)) {
            sender.sendMessage(ChatColor.RED + "Actor o escena inválidos.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Ventana del actor actualizada: " + appearTick + " -> " + disappearTick + ".");
    }

    private void handleActorRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes actor record <start|stop>");
            return;
        }
        if (args[2].equalsIgnoreCase("stop")) {
            saveActorRecording(player);
            return;
        }
        if (!args[2].equalsIgnoreCase("start") || args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes actor record start <scene> <actorId> [duration]");
            return;
        }

        SceneActor actor = manager.getActor(args[3], args[4]);
        if (actor == null) {
            player.sendMessage(ChatColor.RED + "Actor o escena inválidos.");
            return;
        }

        Integer duration = args.length >= 6 ? parseDurationTicks(args[5]) : null;
        if (args.length >= 6 && duration == null) {
            player.sendMessage(ChatColor.RED + "Duración inválida.");
            return;
        }

        stopActorRecording(player.getUniqueId());
        ActorRecordingState state = new ActorRecordingState(args[3], args[4], duration == null ? manager.getCinematic(args[3]).map(Cinematic::getDurationTicks).orElse(200) : duration);
        actorRecordings.put(player.getUniqueId(), state);
        giveSaveRecorderItem(player);
        manager.getCinematic(state.sceneId).ifPresent(cinematic -> actorPreviewService.start(player, cinematic, 0, state.actorId));

        player.showTitle(Title.title(Component.text(ChatColor.YELLOW + "Recording actor en"), Component.text(ChatColor.GOLD + "3")));
        state.countdownTaskTwo = Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.showTitle(Title.title(Component.text(ChatColor.YELLOW + "Recording actor en"), Component.text(ChatColor.GOLD + "2"))),
                20L);
        state.countdownTaskOne = Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.showTitle(Title.title(Component.text(ChatColor.YELLOW + "Recording actor en"), Component.text(ChatColor.GOLD + "1"))),
                40L);
        state.startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> startActorRecordingTask(player, state), 60L);
    }

    private void startActorRecordingTask(Player player, ActorRecordingState state) {
        if (actorRecordings.get(player.getUniqueId()) != state) {
            return;
        }

        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null || !online.isOnline()) {
                stopActorRecording(player.getUniqueId());
                return;
            }
            if (state.tick > state.maxTicks) {
                saveActorRecording(online);
                return;
            }
            state.frames.add(new ActorFrame(state.tick, online.getLocation()));
            manager.getCinematic(state.sceneId).ifPresent(cinematic -> actorPreviewService.tick(online, cinematic, state.tick, state.actorId));
            online.sendActionBar(Component.text(ChatColor.AQUA + "Recording actor " + state.actorId + ChatColor.GRAY + " | Tick " + state.tick + "/" + state.maxTicks));
            state.tick++;
        }, 0L, 1L);
    }

    public boolean isActorSaveItem(ItemStack item) {
        if (item == null || item.getType() != Material.LIME_DYE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Guardar recording actor");
    }

    public void saveActorRecording(Player player) {
        ActorRecordingState state = actorRecordings.get(player.getUniqueId());
        if (state == null) {
            player.sendMessage(ChatColor.RED + "No estás grabando un actor.");
            return;
        }
        if (!manager.saveActorFrames(state.sceneId, state.actorId, state.frames)) {
            player.sendMessage(ChatColor.RED + "No se pudo guardar el recording del actor.");
            return;
        }
        manager.save();
        stopActorRecording(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Recording guardado para actor " + state.actorId + ".");
    }

    public void stopActorRecording(UUID playerId) {
        ActorRecordingState state = actorRecordings.remove(playerId);
        if (state == null) {
            return;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        if (state.startTask != null) {
            state.startTask.cancel();
        }
        if (state.countdownTaskOne != null) {
            state.countdownTaskOne.cancel();
        }
        if (state.countdownTaskTwo != null) {
            state.countdownTaskTwo.cancel();
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            ItemStack slotItem = player.getInventory().getItem(8);
            if (isActorSaveItem(slotItem)) {
                player.getInventory().setItem(8, null);
            }
            actorPreviewService.cleanup(player);
        }
    }

    private void giveSaveRecorderItem(Player player) {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Guardar recording actor (click)");
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(8, item);
    }

    private void handleKey(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes key <add|set|mode|del|list|clear>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleKeyAdd(sender, args);
            case "set" -> handleKeySet(sender, args);
            case "mode" -> handleKeyMode(sender, args);
            case "del" -> handleKeyDel(sender, args);
            case "list" -> handleKeyList(sender, args);
            case "clear" -> handleKeyClear(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Usage: /scenes key <add|set|mode|del|list|clear>");
        }
    }

    private void handleKeyAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use 'here'.");
            return;
        }

        if (args.length < 5 || !"here".equalsIgnoreCase(args[4])) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes key add <scene> <tick> here [smooth|instant]");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid tick.");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args.length >= 6 ? args[5] : null);
        if (interpolationMode == null) {
            sender.sendMessage(ChatColor.RED + "Invalid interpolation. Use smooth or instant.");
            return;
        }

        if (!manager.upsertPoint(args[2], tick, player.getLocation(), interpolationMode)) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframe saved at tick " + tick + ".");
    }

    private void handleKeySet(CommandSender sender, String[] args) {
        if (args.length < 9) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes key set <scene> <tick> x y z yaw pitch [smooth|instant]");
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
            sender.sendMessage(ChatColor.RED + "Invalid numeric values.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command (your current world is used).");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args.length >= 10 ? args[9] : null);
        if (interpolationMode == null) {
            sender.sendMessage(ChatColor.RED + "Invalid interpolation. Use smooth or instant.");
            return;
        }

        Location location = new Location(player.getWorld(), x, y, z, yaw, pitch);
        if (!manager.upsertPoint(args[2], tick, location, interpolationMode)) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframe updated at tick " + tick + ".");
    }

    private void handleKeyDel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes key del <scene> <tick>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid tick.");
            return;
        }

        if (!manager.deletePoint(args[2], tick)) {
            sender.sendMessage(ChatColor.RED + "Scene does not exist or there is no keyframe at that tick.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Keyframe removed at tick " + tick + ".");
    }

    private void handleKeyMode(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes key mode <scene> <tick> <smooth|instant>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid tick.");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args[4]);
        if (interpolationMode == null) {
            sender.sendMessage(ChatColor.RED + "Invalid interpolation. Use smooth or instant.");
            return;
        }

        if (!manager.setPointInterpolation(args[2], tick, interpolationMode)) {
            sender.sendMessage(ChatColor.RED + "Scene does not exist or there is no keyframe at that tick.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Interpolation updated at tick " + tick + " a "
            + interpolationMode.name().toLowerCase(Locale.ROOT) + ".");
    }

    private void handleKeyList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes key list <scene> [page]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        int page = 1;
        if (args.length >= 4) {
            try {
                page = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid page.");
                return;
            }
        }

        int perPage = 8;
        List<CinematicPoint> points = cinematic.getPoints();
        if (points.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "This scene has no keyframes.");
            return;
        }

        int maxPages = (int) Math.ceil(points.size() / (double) perPage);
        int safePage = Math.min(page, maxPages);
        int from = (safePage - 1) * perPage;
        int to = Math.min(points.size(), from + perPage);

        sender.sendMessage(ChatColor.GOLD + "Keyframes for " + cinematic.getId() + " (page " + safePage + "/" + maxPages + "):");
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
            sender.sendMessage(ChatColor.RED + "Usage: /scenes key clear <scene> confirm");
            return;
        }

        if (!manager.clearPoints(args[2])) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "All keyframes were removed.");
    }


    private void handleTickCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes tickcmd <add|remove|list|clear> ...");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleTickCommandAdd(sender, args);
            case "remove" -> handleTickCommandRemove(sender, args);
            case "list" -> handleTickCommandList(sender, args);
            case "clear" -> handleTickCommandClear(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Usage: /scenes tickcmd <add|remove|list|clear> ...");
        }
    }

    private void handleTickCommandAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes tickcmd add <scene> <tick> <command>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid tick.");
            return;
        }

        String command = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        if (!manager.addTickCommand(args[2], tick, command)) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist or the command is invalid.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Command added at tick " + tick + ".");
    }

    private void handleTickCommandRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes tickcmd remove <scene> <tick> <index>");
            return;
        }

        int tick;
        int index;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
            index = Integer.parseInt(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid tick or index.");
            return;
        }

        if (!manager.removeTickCommand(args[2], tick, index)) {
            sender.sendMessage(ChatColor.RED + "Could not remove command. Check scene/tick/index.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Command #" + index + " eliminado en tick " + tick + ".");
    }

    private void handleTickCommandList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes tickcmd list <scene> [tick]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        if (args.length >= 4) {
            int tick;
            try {
                tick = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid tick.");
                return;
            }

            List<String> commands = cinematic.getTickCommands().getOrDefault(tick, List.of());
            if (commands.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "There are no commands at tick " + tick + ".");
                return;
            }

            sender.sendMessage(ChatColor.GOLD + "Commands at tick " + tick + " (" + cinematic.getId() + "):");
            for (int i = 0; i < commands.size(); i++) {
                sender.sendMessage(ChatColor.YELLOW + "#" + (i + 1) + ChatColor.GRAY + " " + commands.get(i));
            }
            return;
        }

        if (cinematic.getTickCommands().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "This scene has no per-tick commands.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Ticks with commands in '" + cinematic.getId() + "':");
        for (Map.Entry<Integer, List<String>> entry : cinematic.getTickCommands().entrySet()) {
            sender.sendMessage(ChatColor.YELLOW + "Tick " + entry.getKey() + ChatColor.GRAY + " -> " + entry.getValue().size() + " command(s)");
        }
    }

    private void handleTickCommandClear(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes tickcmd clear <scene> <all|tick>");
            return;
        }

        Integer tick = null;
        if (!"all".equalsIgnoreCase(args[3])) {
            try {
                tick = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Use all or a valid tick.");
                return;
            }
        }

        if (!manager.clearTickCommands(args[2], tick)) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + (tick == null
                ? "All per-tick commands were removed."
                : "Commands removed at tick " + tick + "."));
    }

    private void handlePlaceholders(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Placeholders disponibles de PlaceholderAPI:");
        sender.sendMessage(ChatColor.GRAY + "Formato general: %scenes_<placeholder>%");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_playing%" + ChatColor.GRAY + " true/false si el jugador está en una escena");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_current_scene%" + ChatColor.GRAY + " id de la escena actual (o none)");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_current_tick%" + ChatColor.GRAY + " tick actual de reproducción");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_end_tick%" + ChatColor.GRAY + " tick final del tramo actual");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_progress_percent%" + ChatColor.GRAY + " progreso de la escena actual (0-100)");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_played_count%" + ChatColor.GRAY + " cantidad de escenas vistas por el jugador");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_played_list%" + ChatColor.GRAY + " lista de escenas vistas separada por comas");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_played_<scene>%" + ChatColor.GRAY + " true/false si el jugador vio esa escena");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_played_<scene>_<player>%" + ChatColor.GRAY + " versión para otro jugador");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_duration_<scene>%" + ChatColor.GRAY + " duración total en ticks");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_points_<scene>%" + ChatColor.GRAY + " cantidad de keyframes");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_commands_<scene>%" + ChatColor.GRAY + " cantidad de comandos por tick");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_scenes_count%" + ChatColor.GRAY + " total de escenas cargadas");
        sender.sendMessage(ChatColor.YELLOW + "%scenes_scenes_list%" + ChatColor.GRAY + " ids de escenas cargadas separadas por comas");
        sender.sendMessage(ChatColor.DARK_AQUA + "También puedes usar placeholders internos en tickcmd (no PAPI):");
        sender.sendMessage(ChatColor.YELLOW + "{player} {player_display_name} {player_uuid} {scene} {tick}");
        sender.sendMessage(ChatColor.YELLOW + "{world} {x} {y} {z} {yaw} {pitch}");
    }

    private void handleFinish(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes finish <scene> <return|stay|teleport_here|teleport>");
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
                    sender.sendMessage(ChatColor.RED + "Only players can use teleport_here.");
                    return;
                }
                endAction = Cinematic.EndAction.teleportTo(player.getLocation());
            }
            case "teleport" -> {
                if (args.length < 9) {
                    sender.sendMessage(ChatColor.RED + "Usage: /scenes finish <scene> teleport <world> <x> <y> <z> <yaw> <pitch>");
                    return;
                }

                World world = Bukkit.getWorld(args[3]);
                if (world == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid world: " + args[3]);
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
                    sender.sendMessage(ChatColor.RED + "Invalid coordinates.");
                    return;
                }

                endAction = Cinematic.EndAction.teleportTo(new Location(world, x, y, z, yaw, pitch));
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Invalid mode. Use return, stay, teleport_here or teleport.");
                return;
            }
        }

        if (!manager.setEndAction(sceneId, endAction)) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Comportamiento final actualizado para '" + sceneId + "'.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes delete <scene>");
            return;
        }
        if (!manager.deleteCinematic(args[1])) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }
        manager.save();
        sender.sendMessage(ChatColor.GREEN + "Scene deleted: " + args[1]);
    }

    private void handleList(CommandSender sender) {
        List<String> ids = manager.getCinematicIds();
        if (ids.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No scenes have been created.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Scenes: " + ChatColor.YELLOW + String.join(", ", ids));
    }

    private void handleShow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /scenes show <scene>");
            return;
        }
        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(ChatColor.RED + "That scene does not exist.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Scene " + cinematic.getId() + ChatColor.GRAY + " -> duration " + cinematic.getDurationTicks() + "t, " + cinematic.getPoints().size() + " keyframes");
        sender.sendMessage(ChatColor.GRAY + "Ending: " + describeEndAction(cinematic.getEndAction()));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "ExtraScenesV2 - commands /" + label + ":");
        sender.sendMessage(ChatColor.YELLOW + "/scenes create <scene> <durationTicks>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes edit <scene>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes play <scene> [startTick] [endTick]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes stop");
        sender.sendMessage(ChatColor.YELLOW + "/scenes record start <scene> [everyTicks] [duration:10s|200t]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes record stop");
        sender.sendMessage(ChatColor.YELLOW + "/scenes record clear <scene> confirm");
        sender.sendMessage(ChatColor.YELLOW + "/scenes actor create <scene> <actorId> [scale]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes actor skin <scene> <actorId> <playerName|texture> [signature]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes actor window <scene> <actorId> <appearTick> <disappearTick>");
        sender.sendMessage(ChatColor.YELLOW + "/scenes actor record start <scene> <actorId> [duration]");
        sender.sendMessage(ChatColor.YELLOW + "/scenes actor record stop");
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
            return "return to the starting point";
        }

        if (endAction.type() == Cinematic.EndActionType.TELEPORT) {
            Location target = endAction.teleportLocation();
            if (target == null || target.getWorld() == null) {
                return "teleport with no destination (use /scenes finish to configure it)";
            }

            return String.format(Locale.US,
                    "teleport to %s %.2f %.2f %.2f %.1f %.1f",
                    target.getWorld().getName(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getYaw(),
                    target.getPitch());
        }

        return "stay at the last camera point";
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
        if (args.length == 2 && args[0].equalsIgnoreCase("actor")) {
            return List.of("create", "skin", "window", "record");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("record")) {
            return List.of("start", "stop");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("record") && args[2].equalsIgnoreCase("start")) {
            return manager.getCinematicIds().stream().filter(id -> id.startsWith(args[3])).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("actor") && List.of("create", "skin", "window").contains(args[1].toLowerCase(Locale.ROOT))) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("actor") && List.of("skin", "window").contains(args[1].toLowerCase(Locale.ROOT))) {
            return manager.getCinematic(args[2])
                    .map(cinematic -> cinematic.getActors().values().stream().map(SceneActor::id).filter(id -> id.startsWith(args[3])).toList())
                    .orElse(Collections.emptyList());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("record") && args[2].equalsIgnoreCase("start")) {
            return manager.getCinematic(args[3])
                    .map(cinematic -> cinematic.getActors().values().stream().map(SceneActor::id).filter(id -> id.startsWith(args[4])).toList())
                    .orElse(Collections.emptyList());
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

    private record SkinData(String texture, String signature) {
    }

    private static final class ActorRecordingState {
        private final String sceneId;
        private final String actorId;
        private final int maxTicks;
        private final List<ActorFrame> frames = new ArrayList<>();
        private int tick;
        private BukkitTask task;
        private BukkitTask startTask;
        private BukkitTask countdownTaskOne;
        private BukkitTask countdownTaskTwo;

        private ActorRecordingState(String sceneId, String actorId, int maxTicks) {
            this.sceneId = sceneId;
            this.actorId = actorId;
            this.maxTicks = Math.max(1, maxTicks);
        }
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
