package com.extracraft.extrascenesv2.commands;

import com.extracraft.extrascenesv2.cinematics.Cinematic;
import com.extracraft.extrascenesv2.cinematics.CinematicAudioTrack;
import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import com.extracraft.extrascenesv2.cinematics.CinematicPoint;
import com.extracraft.extrascenesv2.cinematics.CinematicSubtitleCue;
import com.extracraft.extrascenesv2.cinematics.SceneActor;
import com.extracraft.extrascenesv2.cinematics.ActorFrame;
import com.extracraft.extrascenesv2.cinematics.ActorPlaybackService;
import com.extracraft.extrascenesv2.editor.TimelineEditorService;
import com.extracraft.extrascenesv2.audio.OpenAudioCommandService;
import com.extracraft.extrascenesv2.commands.audio.AudioCommandHandler;
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ExtraScenesCommand implements CommandExecutor, TabCompleter {

    private static final String C_RED = "§c";
    private static final String C_GREEN = "§a";
    private static final String C_YELLOW = "§e";
    private static final String C_GOLD = "§6";
    private static final String C_GRAY = "§7";
    private static final String C_AQUA = "§b";
    private static final String C_DARK_AQUA = "§3";

    private static final List<String> SUBCOMMANDS = List.of("create", "edit", "play", "stop", "record", "actor", "key", "tickcmd", "placeholders", "finish", "players", "audio", "subtitle", "undo", "redo", "delete", "list", "show", "editor", "reload");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern UUID_ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([a-fA-F0-9]{32})\"");
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");
    private static final String PLAYER_SKIN_MODE_TEXTURE = "__viewer_player_skin__";
    private static final String PLAYER_SKIN_MODE_SIGNATURE = "__viewer_player_skin__";

    private final JavaPlugin plugin;
    private final CinematicManager manager;
    private final CinematicPlaybackService playbackService;
    private final Map<UUID, RecordingState> recordings = new HashMap<>();
    private final Map<UUID, ActorRecordingState> actorRecordings = new HashMap<>();
    private final ActorPlaybackService actorPreviewService;
    private final TimelineEditorService timelineEditorService;
    private final OpenAudioCommandService openAudioCommandService;
    private final AudioCommandHandler audioCommandHandler;

    public ExtraScenesCommand(JavaPlugin plugin, CinematicManager manager, CinematicPlaybackService playbackService, TimelineEditorService timelineEditorService) {
        this.plugin = plugin;
        this.manager = manager;
        this.playbackService = playbackService;
        this.timelineEditorService = timelineEditorService;
        this.actorPreviewService = new ActorPlaybackService(plugin);
        this.openAudioCommandService = new OpenAudioCommandService(plugin);
        this.audioCommandHandler = new AudioCommandHandler(manager);
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
            case "players" -> handlePlayers(sender, args);
            case "audio" -> handleAudio(sender, args);
            case "subtitle" -> handleSubtitle(sender, args);
            case "undo" -> handleUndo(sender, args);
            case "redo" -> handleRedo(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "show" -> handleShow(sender, args);
            case "editor" -> handleEditor(sender, args);
            case "reload" -> {
                manager.load();
                sender.sendMessage(C_GREEN + "Scenes reloaded from scene files.");
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes create <scene> <durationTicks>");
            return;
        }

        int durationTicks;
        try {
            durationTicks = Math.max(1, Integer.parseInt(args[2]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "durationTicks must be an integer > 0.");
            return;
        }

        if (!manager.createCinematic(args[1], durationTicks)) {
            sender.sendMessage(C_RED + "A scene with that ID already exists.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Scene created: " + args[1] + " (" + durationTicks + "t)");
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes edit <scene>");
            return;
        }

        Cinematic scene = manager.getCinematic(args[1]).orElse(null);
        if (scene == null) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        sender.sendMessage(C_GOLD + "Edit wizard for '" + scene.getId() + "':");
        sender.sendMessage(C_YELLOW + "- /scenes key add " + scene.getId() + " <tick> here [smooth|instant]");
        sender.sendMessage(C_YELLOW + "- /scenes key set " + scene.getId() + " <tick> x y z yaw pitch [smooth|instant]");
        sender.sendMessage(C_YELLOW + "- /scenes key mode " + scene.getId() + " <tick> <smooth|instant>");
        sender.sendMessage(C_YELLOW + "- /scenes key del " + scene.getId() + " <tick>");
        sender.sendMessage(C_YELLOW + "- /scenes key list " + scene.getId() + " [page]");
        sender.sendMessage(C_YELLOW + "- /scenes finish " + scene.getId() + " <return|stay|teleport_here|teleport>");
        sender.sendMessage(C_YELLOW + "- /scenes players " + scene.getId() + " <hide|show>");
        sender.sendMessage(C_YELLOW + "- /scenes tickcmd add " + scene.getId() + " <tick> <command>");
        sender.sendMessage(C_YELLOW + "- /scenes audio set " + scene.getId() + " files intro_1.mp3 1250");
        sender.sendMessage(C_YELLOW + "- /scenes audio playtemplate " + scene.getId() + " oa play {player} files:intro_1.mp3 {\"startAtMillis\":{millis}}");
        sender.sendMessage(C_YELLOW + "- /scenes subtitle add " + scene.getId() + " <start> <end> <line1>|<line2>");
    }

    private void handleAudio(CommandSender sender, String[] args) {
        audioCommandHandler.handle(sender, args);
    }

    private void handleSubtitle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes subtitle <add|del|clear> <scene> ...");
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if ("clear".equals(mode)) {
            if (!manager.clearSubtitles(args[2])) {
                sender.sendMessage(C_RED + "That scene does not exist.");
                return;
            }
            manager.save();
            sender.sendMessage(C_GREEN + "Subtitles cleared for scene '" + args[2] + "'.");
            return;
        }

        if ("del".equals(mode)) {
            if (args.length < 4) {
                sender.sendMessage(C_RED + "Usage: /scenes subtitle del <scene> <startTick>");
                return;
            }
            int startTick;
            try {
                startTick = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(C_RED + "Invalid startTick.");
                return;
            }
            if (!manager.removeSubtitle(args[2], startTick)) {
                sender.sendMessage(C_RED + "Subtitle/startTick not found.");
                return;
            }
            manager.save();
            sender.sendMessage(C_GREEN + "Subtitle deleted.");
            return;
        }

        if (!"add".equals(mode) || args.length < 6) {
            sender.sendMessage(C_RED + "Usage: /scenes subtitle add <scene> <startTick> <endTick> <line1>|<line2>");
            return;
        }

        int startTick;
        int endTick;
        try {
            startTick = Math.max(0, Integer.parseInt(args[3]));
            endTick = Math.max(startTick, Integer.parseInt(args[4]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "Invalid tick values.");
            return;
        }

        String content = String.join(" ", Arrays.copyOfRange(args, 5, args.length)).replace('&', '\u00A7');
        String[] lines = content.split("\\|", 2);
        String line1 = lines.length >= 1 ? lines[0] : "";
        String line2 = lines.length == 2 ? lines[1] : "";

        if (!manager.upsertSubtitle(args[2], new CinematicSubtitleCue(startTick, endTick, line1, line2))) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Subtitle added. PAPI: %extracraft_sub_1% / %extracraft_sub_2%.");
    }

    private void handleUndo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes undo <scene>");
            return;
        }
        if (!manager.undo(args[1])) {
            sender.sendMessage(C_RED + "No hay cambios para deshacer en esa escena.");
            return;
        }
        sender.sendMessage(C_GREEN + "Undo aplicado a " + args[1] + ".");
    }

    private void handleRedo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes redo <scene>");
            return;
        }
        if (!manager.redo(args[1])) {
            sender.sendMessage(C_RED + "No hay cambios para rehacer en esa escena.");
            return;
        }
        sender.sendMessage(C_GREEN + "Redo aplicado a " + args[1] + ".");
    }

    private void handlePlayers(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes players <scene> <hide|show>");
            return;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        boolean hidePlayers;
        if (mode.equals("hide") || mode.equals("on") || mode.equals("true")) {
            hidePlayers = true;
        } else if (mode.equals("show") || mode.equals("off") || mode.equals("false")) {
            hidePlayers = false;
        } else {
            sender.sendMessage(C_RED + "Modo inválido. Usa hide o show.");
            return;
        }

        if (!manager.setHidePlayersDuringPlayback(args[1], hidePlayers)) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Escena '" + args[1] + "': jugadores " + (hidePlayers ? "ocultos" : "visibles") + " durante la cinemática.");
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes play <scene> [player] [startTick] [endTick]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        Player target = null;
        int argIndex = 2;

        if (args.length >= 3) {
            try {
                Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(C_RED + "Player not found: " + args[2]);
                    return;
                }
                argIndex = 3;
            }
        }

        if (target == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(C_RED + "Console must provide a target player: /scenes play <scene> <player> [startTick] [endTick]");
                return;
            }
            target = player;
        }

        int startTick = 0;
        Integer endTick = null;

        if (args.length >= argIndex + 1) {
            try {
                startTick = Math.max(0, Integer.parseInt(args[argIndex]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(C_RED + "Invalid startTick.");
                return;
            }
        }

        if (args.length >= argIndex + 2) {
            try {
                endTick = Math.max(startTick, Integer.parseInt(args[argIndex + 1]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(C_RED + "Invalid endTick.");
                return;
            }
        }

        if (!playbackService.play(target, cinematic, startTick, endTick)) {
            sender.sendMessage(C_RED + "This scene has no keyframes.");
            return;
        }

        sender.sendMessage(C_GREEN + "Playing scene '" + cinematic.getId() + "' for " + target.getName() + ".");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C_RED + "Only players can use /scenes stop.");
            return;
        }

        if (!playbackService.stop(player)) {
            sender.sendMessage(C_RED + "You are not playing any scene.");
            return;
        }

        sender.sendMessage(C_GREEN + "Scene stopped.");
    }

    private void handleRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C_RED + "Only players can record scenes.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes record <start|stop|clear>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> handleRecordStart(player, args);
            case "stop" -> handleRecordStop(player);
            case "clear" -> handleRecordClear(sender, args);
            default -> sender.sendMessage(C_RED + "Usage: /scenes record <start|stop|clear>");
        }
    }

    private void handleRecordStart(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(C_RED + "Usage: /scenes record start <scene> [everyTicks] [duration:10s|200t]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            player.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        int everyTicks = 1;
        int maxTicks = cinematic.getDurationTicks();

        if (args.length >= 4) {
            try {
                everyTicks = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                player.sendMessage(C_RED + "Invalid everyTicks.");
                return;
            }
        }

        if (args.length >= 5) {
            Integer parsed = parseDurationTicks(args[4]);
            if (parsed == null) {
                player.sendMessage(C_RED + "Invalid duration. Use 10s or 200t.");
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
                current.sendMessage(C_GREEN + "Recording finished at " + state.currentTick + " ticks.");
                manager.save();
                return;
            }

            manager.upsertPoint(state.sceneId, state.currentTick, current.getLocation(), CinematicPoint.InterpolationMode.SMOOTH);
            state.currentTick += state.everyTicks;
        }, 0L, everyTicks);

        recordings.put(player.getUniqueId(), state);
        player.sendMessage(C_GREEN + "Recording started on scene '" + args[2] + "'.");
    }

    private void handleRecordStop(Player player) {
        if (!stopAndRemoveRecording(player.getUniqueId())) {
            player.sendMessage(C_RED + "There is no active recording.");
            return;
        }

        manager.save();
        player.sendMessage(C_GREEN + "Recording stopped.");
    }

    private void handleRecordClear(CommandSender sender, String[] args) {
        if (args.length < 5 || !"confirm".equalsIgnoreCase(args[4])) {
            sender.sendMessage(C_RED + "Usage: /scenes record clear <scene> confirm");
            return;
        }

        if (!manager.clearPoints(args[3])) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Keyframes removed for " + args[3] + ".");
    }

    private void handleActor(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes actor <create|skin|scale|window|record|recordfrom>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> handleActorCreate(sender, args);
            case "skin" -> handleActorSkin(sender, args);
            case "scale" -> handleActorScale(sender, args);
            case "window" -> handleActorWindow(sender, args);
            case "record" -> handleActorRecord(sender, args);
            case "recordfrom" -> handleActorRecordFrom(sender, args);
            default -> sender.sendMessage(C_RED + "Usage: /scenes actor <create|skin|scale|window|record|recordfrom>");
        }
    }

    private void handleActorCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(C_RED + "Usage: /scenes actor create <scene> <actorId> [scale]");
            return;
        }

        Double scale = 1.0D;
        if (args.length >= 5) {
            scale = parseScale(args[4]);
            if (scale == null) {
                sender.sendMessage(C_RED + "Escala inválida. Usa un número entre 0.0625 y 16 (soporta coma o punto).");
                return;
            }
        }

        if (!manager.upsertActor(args[2], args[3], args[3], scale, null, null)) {
            sender.sendMessage(C_RED + "No existe esa escena.");
            return;
        }
        manager.save();
        sender.sendMessage(C_GREEN + "Actor guardado.");
    }

    private void handleActorSkin(CommandSender sender, String[] args) {
        if (args.length != 5) {
            sender.sendMessage(C_RED + "Usage: /scenes actor skin <scene> <actorId> <player|playerName>");
            return;
        }

        SceneActor actor = manager.getActor(args[2], args[3]);
        if (actor == null) {
            sender.sendMessage(C_RED + "Actor no existe. Créalo primero.");
            return;
        }

        String input = args[4].trim();
        String texture;
        String signature;

        if ("player".equalsIgnoreCase(input)) {
            texture = PLAYER_SKIN_MODE_TEXTURE;
            signature = PLAYER_SKIN_MODE_SIGNATURE;
        } else {
            SkinData skinData = fetchSkinByName(input);
            if (skinData == null) {
                sender.sendMessage(C_RED + "No se pudo resolver la skin premium de ese usuario.");
                return;
            }
            texture = skinData.texture();
            signature = skinData.signature();
        }

        if (!manager.upsertActor(args[2], args[3], actor.displayName(), actor.scale(), texture, signature)) {
            sender.sendMessage(C_RED + "No se pudo actualizar skin.");
            return;
        }
        manager.save();

        if ("player".equalsIgnoreCase(input)) {
            sender.sendMessage(C_GREEN + "Skin del actor configurada para usar la skin del jugador espectador.");
        } else {
            sender.sendMessage(C_GREEN + "Skin del actor guardada para " + input + ".");
        }
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

    private void handleActorScale(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(C_RED + "Usage: /scenes actor scale <scene> <actorId> <scale>");
            return;
        }

        SceneActor actor = manager.getActor(args[2], args[3]);
        if (actor == null) {
            sender.sendMessage(C_RED + "Actor no existe. Créalo primero.");
            return;
        }

        Double parsedScale = parseScale(args[4]);
        if (parsedScale == null) {
            sender.sendMessage(C_RED + "Escala inválida. Usa un número entre 0.0625 y 16 (soporta coma o punto).");
            return;
        }

        double scale = parsedScale;

        if (!manager.upsertActor(args[2], args[3], actor.displayName(), scale, actor.skinTexture(), actor.skinSignature())) {
            sender.sendMessage(C_RED + "No se pudo actualizar el scale del actor.");
            return;
        }

        manager.save();

        SceneActor updatedActor = manager.getActor(args[2], args[3]);
        double effectiveScale = updatedActor == null ? scale : updatedActor.scale();
        if (Math.abs(effectiveScale - scale) > 0.0001D) {
            sender.sendMessage(C_YELLOW + "Scale solicitado: " + scale + ", aplicado: " + effectiveScale + " (ajustado al rango permitido).");
        } else {
            sender.sendMessage(C_GREEN + "Scale del actor actualizado a " + effectiveScale + ".");
        }
    }


    private Double parseScale(String rawScale) {
        if (rawScale == null) {
            return null;
        }

        String normalized = rawScale.trim().replace(',', '.');
        if (normalized.isEmpty()) {
            return null;
        }

        try {
            double value = Double.parseDouble(normalized);
            if (!Double.isFinite(value)) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void handleActorWindow(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(C_RED + "Usage: /scenes actor window <scene> <actorId> <appearTick> <disappearTick>");
            return;
        }

        int appearTick;
        int disappearTick;
        try {
            appearTick = Math.max(0, Integer.parseInt(args[4]));
            disappearTick = Math.max(appearTick, Integer.parseInt(args[5]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "Ticks inválidos.");
            return;
        }

        if (!manager.setActorWindow(args[2], args[3], appearTick, disappearTick)) {
            sender.sendMessage(C_RED + "Actor o escena inválidos.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Ventana del actor actualizada: " + appearTick + " -> " + disappearTick + ".");
    }

    private void handleActorRecordFrom(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C_RED + "Solo jugadores.");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(C_RED + "Usage: /scenes actor recordfrom <scene> <actorId> <startTick|current> [duration]");
            return;
        }

        Cinematic scene = manager.getCinematic(args[2]).orElse(null);
        SceneActor actor = manager.getActor(args[2], args[3]);
        if (scene == null || actor == null) {
            sender.sendMessage(C_RED + "Actor o escena inválidos.");
            return;
        }

        int startTick;
        if (args[4].equalsIgnoreCase("current")) {
            if (!playbackService.hasPlaybackState(player.getUniqueId())) {
                sender.sendMessage(C_RED + "No hay reproducción/editor activo para usar 'current'.");
                return;
            }
            String currentSceneId = playbackService.getCurrentSceneId(player.getUniqueId());
            if (!currentSceneId.equalsIgnoreCase(args[2])) {
                sender.sendMessage(C_RED + "'current' solo puede usarse si estás en la misma escena ('" + currentSceneId + "').");
                return;
            }
            startTick = playbackService.getCurrentTick(player.getUniqueId());
        } else {
            try {
                startTick = Math.max(0, Integer.parseInt(args[4]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(C_RED + "startTick inválido.");
                return;
            }
        }

        Integer duration = args.length >= 6 ? parseDurationTicks(args[5]) : null;
        if (args.length >= 6 && duration == null) {
            sender.sendMessage(C_RED + "Duración inválida.");
            return;
        }

        ActorFrame anchor = findActorFrameAtTick(actor, startTick);
        if (anchor != null && anchor.location() != null) {
            player.teleport(anchor.location());
        }

        stopActorRecording(player.getUniqueId());
        ActorRecordingState state = new ActorRecordingState(args[2], args[3], duration == null ? scene.getDurationTicks() : duration, startTick);
        actorRecordings.put(player.getUniqueId(), state);
        giveSaveRecorderItem(player);
        actorPreviewService.start(player, scene, startTick, state.actorId);
        playbackService.syncSubtitleForTick(player, scene, startTick);

        player.sendMessage(C_GREEN + "Preparado recordfrom tick " + startTick + ". Cuenta regresiva iniciada.");
        player.showTitle(Title.title(Component.text(C_YELLOW + "Recording actor en"), Component.text(C_GOLD + "3")));
        state.countdownTaskTwo = Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.showTitle(Title.title(Component.text(C_YELLOW + "Recording actor en"), Component.text(C_GOLD + "2"))),
                20L);
        state.countdownTaskOne = Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.showTitle(Title.title(Component.text(C_YELLOW + "Recording actor en"), Component.text(C_GOLD + "1"))),
                40L);
        state.startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> startActorRecordingTask(player, state), 60L);
    }

    private ActorFrame findActorFrameAtTick(SceneActor actor, int tick) {
        if (actor == null || actor.frames().isEmpty()) {
            return null;
        }
        ActorFrame candidate = actor.frames().get(0);
        for (ActorFrame frame : actor.frames()) {
            if (frame.tick() <= tick) {
                candidate = frame;
                continue;
            }
            break;
        }
        return candidate;
    }

    private void handleActorRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C_RED + "Solo jugadores.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes actor record <start|stop>");
            return;
        }
        if (args[2].equalsIgnoreCase("stop")) {
            saveActorRecording(player);
            return;
        }
        if (!args[2].equalsIgnoreCase("start") || args.length < 5) {
            sender.sendMessage(C_RED + "Usage: /scenes actor record start <scene> <actorId> [duration]");
            return;
        }

        SceneActor actor = manager.getActor(args[3], args[4]);
        if (actor == null) {
            player.sendMessage(C_RED + "Actor o escena inválidos.");
            return;
        }

        Integer duration = args.length >= 6 ? parseDurationTicks(args[5]) : null;
        if (args.length >= 6 && duration == null) {
            player.sendMessage(C_RED + "Duración inválida.");
            return;
        }

        stopActorRecording(player.getUniqueId());
        ActorRecordingState state = new ActorRecordingState(args[3], args[4], duration == null ? manager.getCinematic(args[3]).map(Cinematic::getDurationTicks).orElse(200) : duration, 0);
        actorRecordings.put(player.getUniqueId(), state);
        giveSaveRecorderItem(player);
        manager.getCinematic(state.sceneId).ifPresent(cinematic -> {
            actorPreviewService.start(player, cinematic, 0, state.actorId);
            playbackService.syncSubtitleForTick(player, cinematic, 0);
        });

        player.showTitle(Title.title(Component.text(C_YELLOW + "Recording actor en"), Component.text(C_GOLD + "3")));
        state.countdownTaskTwo = Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.showTitle(Title.title(Component.text(C_YELLOW + "Recording actor en"), Component.text(C_GOLD + "2"))),
                20L);
        state.countdownTaskOne = Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.showTitle(Title.title(Component.text(C_YELLOW + "Recording actor en"), Component.text(C_GOLD + "1"))),
                40L);
        state.startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> startActorRecordingTask(player, state), 60L);
    }

    private void startActorRecordingTask(Player player, ActorRecordingState state) {
        if (actorRecordings.get(player.getUniqueId()) != state) {
            return;
        }

        startActorRecordingAudio(player, state);

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
            state.frames.add(new ActorFrame(state.tick, online.getLocation(), online.getEyeLocation().getYaw(), online.getPose().name()));
            manager.getCinematic(state.sceneId).ifPresent(cinematic -> {
                actorPreviewService.tick(online, cinematic, state.tick, state.actorId);
                playbackService.syncSubtitleForTick(online, cinematic, state.tick);
            });

            if (!persistActorRecordingProgress(state)) {
                online.sendMessage(C_RED + "No se pudo persistir el recording del actor. Grabación detenida para evitar pérdida de datos.");
                stopActorRecording(online.getUniqueId());
                return;
            }

            online.sendActionBar(Component.text(buildRecordingActionBar(state)));
            state.tick++;
        }, 0L, 1L);
    }

    private void previewSubtitleAtTick(Player player, Cinematic cinematic, int tick) {
        if (player == null || cinematic == null) {
            return;
        }
        CinematicSubtitleCue cue = cinematic.getSubtitleAtTick(tick);
        String line1 = cue == null ? "" : cue.line1();
        String line2 = cue == null ? "" : cue.line2();
        if (line1.isBlank() && line2.isBlank()) {
            return;
        }
        player.sendActionBar(Component.text(C_AQUA + line1 + (line2.isBlank() ? "" : C_GRAY + " | " + C_AQUA + line2)));
    }

    private void startActorRecordingAudio(Player player, ActorRecordingState state) {
        Cinematic cinematic = manager.getCinematic(state.sceneId).orElse(null);
        if (cinematic == null) {
            return;
        }

        CinematicAudioTrack track = cinematic.getAudioTrack();
        if (track == null || !track.isConfigured()) {
            return;
        }

        int seekMillis = Math.max(0, track.startAtMillis() + (state.tick * 50));
        String payload = track.renderPlayCommand(player.getName(), seekMillis);
        openAudioCommandService.dispatch(payload, "actor-record-start scene=" + state.sceneId + " actor=" + state.actorId + " player=" + player.getName());

        state.audioStopCommand = track.renderStopCommand(player.getName());
        state.audioPlaying = true;
    }

    private void stopActorRecordingAudio(ActorRecordingState state) {
        if (state == null || !state.audioPlaying || state.audioStopCommand == null || state.audioStopCommand.isBlank()) {
            return;
        }
        String stopCommand = state.audioStopCommand.startsWith("/") ? state.audioStopCommand.substring(1) : state.audioStopCommand;
        openAudioCommandService.dispatch(stopCommand, "actor-record-stop scene=" + state.sceneId + " actor=" + state.actorId);
        state.audioPlaying = false;
    }

    public boolean isActorSaveItem(ItemStack item) {
        if (item == null || item.getType() != Material.LIME_DYE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return false;
        }

        String plainName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return plainName.contains("Guardar recording actor");
    }

    public void saveActorRecording(Player player) {
        ActorRecordingState state = actorRecordings.get(player.getUniqueId());
        if (state == null) {
            player.sendMessage(C_RED + "No estás grabando un actor.");
            return;
        }
        if (!persistActorRecordingProgress(state)) {
            player.sendMessage(C_RED + "No se pudo guardar el recording del actor.");
            return;
        }
        stopActorRecording(player.getUniqueId());
        player.sendMessage(C_GREEN + "Recording guardado para actor " + state.actorId + ".");
    }

    public void stopActorRecording(UUID playerId) {
        ActorRecordingState state = actorRecordings.remove(playerId);
        if (state == null) {
            return;
        }
        persistActorRecordingProgress(state);
        stopActorRecordingAudio(state);
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
            playbackService.clearSubtitleLines(playerId);
            player.sendActionBar(Component.empty());
        }
    }

    private boolean persistActorRecordingProgress(ActorRecordingState state) {
        if (state == null || state.frames.isEmpty()) {
            return true;
        }
        if (!manager.saveActorFrames(state.sceneId, state.actorId, state.frames)) {
            return false;
        }
        manager.save();
        return true;
    }

    private void giveSaveRecorderItem(Player player) {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Guardar recording actor (click)"));
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(8, item);
    }

    private void handleKey(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes key <add|set|mode|del|list|clear>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleKeyAdd(sender, args);
            case "set" -> handleKeySet(sender, args);
            case "mode" -> handleKeyMode(sender, args);
            case "del" -> handleKeyDel(sender, args);
            case "list" -> handleKeyList(sender, args);
            case "clear" -> handleKeyClear(sender, args);
            default -> sender.sendMessage(C_RED + "Usage: /scenes key <add|set|mode|del|list|clear>");
        }
    }

    private void handleKeyAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C_RED + "Only players can use 'here'.");
            return;
        }

        if (args.length < 5 || !"here".equalsIgnoreCase(args[4])) {
            sender.sendMessage(C_RED + "Usage: /scenes key add <scene> <tick> here [smooth|instant]");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "Invalid tick.");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args.length >= 6 ? args[5] : null);
        if (interpolationMode == null) {
            sender.sendMessage(C_RED + "Invalid interpolation. Use smooth or instant.");
            return;
        }

        if (!manager.upsertPoint(args[2], tick, player.getLocation(), interpolationMode)) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Keyframe saved at tick " + tick + ".");
    }

    private void handleKeySet(CommandSender sender, String[] args) {
        if (args.length < 9) {
            sender.sendMessage(C_RED + "Usage: /scenes key set <scene> <tick> x y z yaw pitch [smooth|instant]");
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
            sender.sendMessage(C_RED + "Invalid numeric values.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(C_RED + "Only players can use this command (your current world is used).");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args.length >= 10 ? args[9] : null);
        if (interpolationMode == null) {
            sender.sendMessage(C_RED + "Invalid interpolation. Use smooth or instant.");
            return;
        }

        Location location = new Location(player.getWorld(), x, y, z, yaw, pitch);
        if (!manager.upsertPoint(args[2], tick, location, interpolationMode)) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Keyframe updated at tick " + tick + ".");
    }

    private void handleKeyDel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(C_RED + "Usage: /scenes key del <scene> <tick>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "Invalid tick.");
            return;
        }

        if (!manager.deletePoint(args[2], tick)) {
            sender.sendMessage(C_RED + "Scene does not exist or there is no keyframe at that tick.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Keyframe removed at tick " + tick + ".");
    }

    private void handleKeyMode(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(C_RED + "Usage: /scenes key mode <scene> <tick> <smooth|instant>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "Invalid tick.");
            return;
        }

        CinematicPoint.InterpolationMode interpolationMode = parseInterpolationMode(args[4]);
        if (interpolationMode == null) {
            sender.sendMessage(C_RED + "Invalid interpolation. Use smooth or instant.");
            return;
        }

        if (!manager.setPointInterpolation(args[2], tick, interpolationMode)) {
            sender.sendMessage(C_RED + "Scene does not exist or there is no keyframe at that tick.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Interpolation updated at tick " + tick + " a "
            + interpolationMode.name().toLowerCase(Locale.ROOT) + ".");
    }

    private void handleKeyList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes key list <scene> [page]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        int page = 1;
        if (args.length >= 4) {
            try {
                page = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(C_RED + "Invalid page.");
                return;
            }
        }

        int perPage = 8;
        List<CinematicPoint> points = cinematic.getPoints();
        if (points.isEmpty()) {
            sender.sendMessage(C_YELLOW + "This scene has no keyframes.");
            return;
        }

        int maxPages = (int) Math.ceil(points.size() / (double) perPage);
        int safePage = Math.min(page, maxPages);
        int from = (safePage - 1) * perPage;
        int to = Math.min(points.size(), from + perPage);

        sender.sendMessage(C_GOLD + "Keyframes for " + cinematic.getId() + " (page " + safePage + "/" + maxPages + "):");
        for (int i = from; i < to; i++) {
            CinematicPoint point = points.get(i);
            Location loc = point.location();
            sender.sendMessage(C_YELLOW + "t=" + point.tick() + C_GRAY + " ["
                + point.interpolationMode().name().toLowerCase(Locale.ROOT) + "] -> "
                + String.format(Locale.US, "%.2f %.2f %.2f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        }
    }

    private void handleKeyClear(CommandSender sender, String[] args) {
        if (args.length < 4 || !"confirm".equalsIgnoreCase(args[3])) {
            sender.sendMessage(C_RED + "Usage: /scenes key clear <scene> confirm");
            return;
        }

        if (!manager.clearPoints(args[2])) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "All keyframes were removed.");
    }


    private void handleTickCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes tickcmd <add|remove|list|clear> ...");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleTickCommandAdd(sender, args);
            case "remove" -> handleTickCommandRemove(sender, args);
            case "list" -> handleTickCommandList(sender, args);
            case "clear" -> handleTickCommandClear(sender, args);
            default -> sender.sendMessage(C_RED + "Usage: /scenes tickcmd <add|remove|list|clear> ...");
        }
    }

    private void handleTickCommandAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(C_RED + "Usage: /scenes tickcmd add <scene> <tick> <command>");
            return;
        }

        int tick;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "Invalid tick.");
            return;
        }

        String command = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        if (!manager.addTickCommand(args[2], tick, command)) {
            sender.sendMessage(C_RED + "That scene does not exist or the command is invalid.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Command added at tick " + tick + ".");
    }

    private void handleTickCommandRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(C_RED + "Usage: /scenes tickcmd remove <scene> <tick> <index>");
            return;
        }

        int tick;
        int index;
        try {
            tick = Math.max(0, Integer.parseInt(args[3]));
            index = Integer.parseInt(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(C_RED + "Invalid tick or index.");
            return;
        }

        if (!manager.removeTickCommand(args[2], tick, index)) {
            sender.sendMessage(C_RED + "Could not remove command. Check scene/tick/index.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Command #" + index + " eliminado en tick " + tick + ".");
    }

    private void handleTickCommandList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes tickcmd list <scene> [tick]");
            return;
        }

        Cinematic cinematic = manager.getCinematic(args[2]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        if (args.length >= 4) {
            int tick;
            try {
                tick = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(C_RED + "Invalid tick.");
                return;
            }

            List<String> commands = cinematic.getTickCommands().getOrDefault(tick, List.of());
            if (commands.isEmpty()) {
                sender.sendMessage(C_YELLOW + "There are no commands at tick " + tick + ".");
                return;
            }

            sender.sendMessage(C_GOLD + "Commands at tick " + tick + " (" + cinematic.getId() + "):");
            for (int i = 0; i < commands.size(); i++) {
                sender.sendMessage(C_YELLOW + "#" + (i + 1) + C_GRAY + " " + commands.get(i));
            }
            return;
        }

        if (cinematic.getTickCommands().isEmpty()) {
            sender.sendMessage(C_YELLOW + "This scene has no per-tick commands.");
            return;
        }

        sender.sendMessage(C_GOLD + "Ticks with commands in '" + cinematic.getId() + "':");
        for (Map.Entry<Integer, List<String>> entry : cinematic.getTickCommands().entrySet()) {
            sender.sendMessage(C_YELLOW + "Tick " + entry.getKey() + C_GRAY + " -> " + entry.getValue().size() + " command(s)");
        }
    }

    private void handleTickCommandClear(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(C_RED + "Usage: /scenes tickcmd clear <scene> <all|tick>");
            return;
        }

        Integer tick = null;
        if (!"all".equalsIgnoreCase(args[3])) {
            try {
                tick = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(C_RED + "Use all or a valid tick.");
                return;
            }
        }

        if (!manager.clearTickCommands(args[2], tick)) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + (tick == null
                ? "All per-tick commands were removed."
                : "Commands removed at tick " + tick + "."));
    }

    private void handlePlaceholders(CommandSender sender) {
        sender.sendMessage(C_GOLD + "Placeholders disponibles de PlaceholderAPI:");
        sender.sendMessage(C_GRAY + "Formato general: %scenes_<placeholder>%");
        sender.sendMessage(C_YELLOW + "%scenes_playing%" + C_GRAY + " true/false si el jugador está en una escena");
        sender.sendMessage(C_YELLOW + "%scenes_current_scene%" + C_GRAY + " id de la escena actual (o none)");
        sender.sendMessage(C_YELLOW + "%scenes_current_tick%" + C_GRAY + " tick actual de reproducción");
        sender.sendMessage(C_YELLOW + "%scenes_end_tick%" + C_GRAY + " tick final del tramo actual");
        sender.sendMessage(C_YELLOW + "%scenes_progress_percent%" + C_GRAY + " progreso de la escena actual (0-100)");
        sender.sendMessage(C_YELLOW + "%scenes_played_count%" + C_GRAY + " cantidad de escenas vistas por el jugador");
        sender.sendMessage(C_YELLOW + "%scenes_played_list%" + C_GRAY + " lista de escenas vistas separada por comas");
        sender.sendMessage(C_YELLOW + "%scenes_played_<scene>%" + C_GRAY + " true/false si el jugador vio esa escena");
        sender.sendMessage(C_YELLOW + "%scenes_played_<scene>_<player>%" + C_GRAY + " versión para otro jugador");
        sender.sendMessage(C_YELLOW + "%scenes_duration_<scene>%" + C_GRAY + " duración total en ticks");
        sender.sendMessage(C_YELLOW + "%scenes_points_<scene>%" + C_GRAY + " cantidad de keyframes");
        sender.sendMessage(C_YELLOW + "%scenes_commands_<scene>%" + C_GRAY + " cantidad de comandos por tick");
        sender.sendMessage(C_YELLOW + "%scenes_scenes_count%" + C_GRAY + " total de escenas cargadas");
        sender.sendMessage(C_YELLOW + "%scenes_scenes_list%" + C_GRAY + " ids de escenas cargadas separadas por comas");
        sender.sendMessage(C_DARK_AQUA + "También puedes usar placeholders internos en tickcmd (no PAPI):");
        sender.sendMessage(C_YELLOW + "{player} {player_display_name} {player_uuid} {scene} {tick}");
        sender.sendMessage(C_YELLOW + "{world} {x} {y} {z} {yaw} {pitch}");
    }

    private void handleFinish(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(C_RED + "Usage: /scenes finish <scene> <return|stay|teleport_here|teleport>");
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
                    sender.sendMessage(C_RED + "Only players can use teleport_here.");
                    return;
                }
                endAction = Cinematic.EndAction.teleportTo(player.getLocation());
            }
            case "teleport" -> {
                if (args.length < 9) {
                    sender.sendMessage(C_RED + "Usage: /scenes finish <scene> teleport <world> <x> <y> <z> <yaw> <pitch>");
                    return;
                }

                World world = Bukkit.getWorld(args[3]);
                if (world == null) {
                    sender.sendMessage(C_RED + "Invalid world: " + args[3]);
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
                    sender.sendMessage(C_RED + "Invalid coordinates.");
                    return;
                }

                endAction = Cinematic.EndAction.teleportTo(new Location(world, x, y, z, yaw, pitch));
            }
            default -> {
                sender.sendMessage(C_RED + "Invalid mode. Use return, stay, teleport_here or teleport.");
                return;
            }
        }

        if (!manager.setEndAction(sceneId, endAction)) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage(C_GREEN + "Comportamiento final actualizado para '" + sceneId + "'.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes delete <scene>");
            return;
        }
        if (!manager.deleteCinematic(args[1])) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }
        manager.save();
        sender.sendMessage(C_GREEN + "Scene deleted: " + args[1]);
    }

    private void handleList(CommandSender sender) {
        List<String> ids = manager.getCinematicIds();
        if (ids.isEmpty()) {
            sender.sendMessage(C_YELLOW + "No scenes have been created.");
            return;
        }
        sender.sendMessage(C_GOLD + "Scenes: " + C_YELLOW + String.join(", ", ids));
    }

    private void handleShow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(C_RED + "Usage: /scenes show <scene>");
            return;
        }
        Cinematic cinematic = manager.getCinematic(args[1]).orElse(null);
        if (cinematic == null) {
            sender.sendMessage(C_RED + "That scene does not exist.");
            return;
        }

        sender.sendMessage(C_GOLD + "Scene " + cinematic.getId() + C_GRAY + " -> duration " + cinematic.getDurationTicks() + "t, " + cinematic.getPoints().size() + " keyframes");
        sender.sendMessage(C_GRAY + "Ending: " + describeEndAction(cinematic.getEndAction()));
        sender.sendMessage(C_GRAY + "Players during playback: " + (cinematic.shouldHidePlayersDuringPlayback() ? "hidden" : "visible"));
    }

    private void handleEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C_RED + "Solo jugadores pueden usar el editor.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(C_RED + "Usage: /scenes editor <open|close|play|pause|seek|to|actor|record>");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "open" -> {
                if (args.length < 3) {
                    player.sendMessage(C_RED + "Usage: /scenes editor open <scene> [startTick]");
                    return;
                }
                int startTick = 0;
                if (args.length >= 4) {
                    try {
                        startTick = Math.max(0, Integer.parseInt(args[3]));
                    } catch (NumberFormatException ex) {
                        player.sendMessage(C_RED + "startTick inválido.");
                        return;
                    }
                }
                if (!timelineEditorService.open(player, args[2], startTick)) {
                    player.sendMessage(C_RED + "No se pudo abrir el editor para esa escena.");
                }
            }
            case "close" -> {
                if (!timelineEditorService.close(player)) {
                    player.sendMessage(C_RED + "No tenías un editor abierto.");
                }
            }
            case "play" -> {
                if (!timelineEditorService.play(player)) {
                    player.sendMessage(C_RED + "No hay sesión de editor activa.");
                }
            }
            case "pause" -> {
                if (!timelineEditorService.pause(player)) {
                    player.sendMessage(C_RED + "No hay sesión de editor activa.");
                }
            }
            case "seek" -> {
                if (args.length < 3) {
                    player.sendMessage(C_RED + "Usage: /scenes editor seek <deltaTicks>");
                    return;
                }
                int delta;
                try {
                    delta = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    player.sendMessage(C_RED + "deltaTicks inválido.");
                    return;
                }
                if (!timelineEditorService.seek(player, delta)) {
                    player.sendMessage(C_RED + "No hay sesión de editor activa.");
                }
            }
            case "to" -> {
                if (args.length < 3) {
                    player.sendMessage(C_RED + "Usage: /scenes editor to <tick>");
                    return;
                }
                int tick;
                try {
                    tick = Math.max(0, Integer.parseInt(args[2]));
                } catch (NumberFormatException ex) {
                    player.sendMessage(C_RED + "tick inválido.");
                    return;
                }
                if (!timelineEditorService.seekTo(player, tick)) {
                    player.sendMessage(C_RED + "No hay sesión de editor activa.");
                }
            }
            case "actor" -> {
                if (args.length < 3) {
                    player.sendMessage(C_RED + "Usage: /scenes editor actor <actorId>");
                    return;
                }
                if (!timelineEditorService.setSelectedActor(player, args[2])) {
                    player.sendMessage(C_RED + "Actor inválido o no hay sesión de editor activa.");
                    return;
                }
                player.sendMessage(C_GREEN + "Actor del editor seleccionado: " + args[2]);
            }
            case "record" -> {
                Integer durationTicks = null;
                if (args.length >= 3) {
                    durationTicks = parseDurationTicks(args[2]);
                    if (durationTicks == null) {
                        player.sendMessage(C_RED + "Duración inválida. Usa 10s o 200t.");
                        return;
                    }
                }
                if (!timelineEditorService.startSelectedActorRecording(player, durationTicks)) {
                    player.sendMessage(C_RED + "No se pudo iniciar recording desde editor.");
                }
            }
            default -> player.sendMessage(C_RED + "Usage: /scenes editor <open|close|play|pause|seek|to|actor|record>");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(C_GOLD + "ExtraScenesV2 - commands /" + label + ":");
        sender.sendMessage(C_YELLOW + "/scenes create <scene> <durationTicks>");
        sender.sendMessage(C_YELLOW + "/scenes edit <scene>");
        sender.sendMessage(C_YELLOW + "/scenes play <scene> [player] [startTick] [endTick]");
        sender.sendMessage(C_YELLOW + "/scenes stop");
        sender.sendMessage(C_YELLOW + "/scenes record start <scene> [everyTicks] [duration:10s|200t]");
        sender.sendMessage(C_YELLOW + "/scenes record stop");
        sender.sendMessage(C_YELLOW + "/scenes record clear <scene> confirm");
        sender.sendMessage(C_YELLOW + "/scenes actor create <scene> <actorId> [scale]");
        sender.sendMessage(C_YELLOW + "/scenes actor skin <scene> <actorId> <player|playerName>");
        sender.sendMessage(C_YELLOW + "/scenes actor scale <scene> <actorId> <scale>");
        sender.sendMessage(C_YELLOW + "/scenes actor window <scene> <actorId> <appearTick> <disappearTick>");
        sender.sendMessage(C_YELLOW + "/scenes actor record start <scene> <actorId> [duration]");
        sender.sendMessage(C_YELLOW + "/scenes actor record stop");
        sender.sendMessage(C_YELLOW + "/scenes key add <scene> <tick> here [smooth|instant]");
        sender.sendMessage(C_YELLOW + "/scenes key set <scene> <tick> x y z yaw pitch [smooth|instant]");
        sender.sendMessage(C_YELLOW + "/scenes key mode <scene> <tick> <smooth|instant>");
        sender.sendMessage(C_YELLOW + "/scenes key del <scene> <tick>");
        sender.sendMessage(C_YELLOW + "/scenes key list <scene> [page]");
        sender.sendMessage(C_YELLOW + "/scenes key clear <scene> confirm");
        sender.sendMessage(C_YELLOW + "/scenes finish <scene> <return|stay|teleport_here|teleport>");
        sender.sendMessage(C_YELLOW + "/scenes players <scene> <hide|show>");
        sender.sendMessage(C_YELLOW + "/scenes tickcmd <add|remove|list|clear> ...");
        sender.sendMessage(C_YELLOW + "/scenes audio <set|clear|playtemplate|stoptemplate|show> ...");
        sender.sendMessage(C_YELLOW + "/scenes editor <open|close|play|pause|seek|to|actor|record>");
        sender.sendMessage(C_YELLOW + "/scenes placeholders");
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


    private String buildRecordingActionBar(ActorRecordingState state) {
        return C_AQUA + "Recording actor " + state.actorId + C_GRAY + " | Tick " + state.tick + "/" + state.maxTicks;
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


    public void shutdown() {
        for (UUID playerId : recordings.keySet().toArray(UUID[]::new)) {
            stopAndRemoveRecording(playerId);
        }
        for (UUID playerId : actorRecordings.keySet().toArray(UUID[]::new)) {
            stopActorRecording(playerId);
        }
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
            return List.of("create", "skin", "scale", "window", "record", "recordfrom");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("audio")) {
            return List.of("set", "clear", "playtemplate", "stoptemplate", "show");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("audio")) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("editor")) {
            return List.of("open", "close", "play", "pause", "seek", "to", "actor", "record");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("editor") && args[1].equalsIgnoreCase("open")) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("editor") && args[1].equalsIgnoreCase("record")) {
            return List.of("10s", "200t");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("editor") && args[1].equalsIgnoreCase("actor") && sender instanceof Player player) {
            String sceneId = timelineEditorService.getSceneId(player);
            if (sceneId == null) {
                return Collections.emptyList();
            }
            return manager.getCinematic(sceneId)
                    .map(cinematic -> cinematic.getActors().values().stream().map(SceneActor::id).filter(id -> id.startsWith(args[2])).toList())
                    .orElse(Collections.emptyList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("record")) {
            return List.of("start", "stop");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("record") && args[2].equalsIgnoreCase("start")) {
            return manager.getCinematicIds().stream().filter(id -> id.startsWith(args[3])).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("actor") && List.of("create", "skin", "scale", "window").contains(args[1].toLowerCase(Locale.ROOT))) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("actor") && List.of("skin", "scale", "window").contains(args[1].toLowerCase(Locale.ROOT))) {
            return manager.getCinematic(args[2])
                    .map(cinematic -> cinematic.getActors().values().stream().map(SceneActor::id).filter(id -> id.startsWith(args[3])).toList())
                    .orElse(Collections.emptyList());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("skin")) {
            List<String> options = new ArrayList<>();
            options.add("player");
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(options::add);
            return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(args[4].toLowerCase(Locale.ROOT))).toList();
        }


        if (args.length == 5 && args[0].equalsIgnoreCase("actor") && args[1].equalsIgnoreCase("record") && args[2].equalsIgnoreCase("start")) {
            return manager.getCinematic(args[3])
                    .map(cinematic -> cinematic.getActors().values().stream().map(SceneActor::id).filter(id -> id.startsWith(args[4])).toList())
                    .orElse(Collections.emptyList());
        }


        if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            List<String> options = new ArrayList<>();
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(options::add);
            options.add("0");
            return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }

        if (args.length == 3 && Arrays.asList("edit", "delete", "show").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.emptyList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("players")) {
            return manager.getCinematicIds().stream().filter(s -> s.startsWith(args[2])).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("players")) {
            return List.of("hide", "show", "on", "off");
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
        private String audioStopCommand;
        private boolean audioPlaying;

        private ActorRecordingState(String sceneId, String actorId, int maxTicks, int startTick) {
            this.sceneId = sceneId;
            this.actorId = actorId;
            this.maxTicks = Math.max(startTick, Math.max(1, maxTicks));
            this.tick = Math.max(0, startTick);
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

    private record SkinData(String texture, String signature) {
    }
}
