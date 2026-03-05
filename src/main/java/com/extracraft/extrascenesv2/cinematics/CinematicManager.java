package com.extracraft.extrascenesv2.cinematics;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CinematicManager {

    private final JavaPlugin plugin;
    private final Map<String, Cinematic> cinematics = new LinkedHashMap<>();
    private static final int HISTORY_LIMIT = 100;
    private final Map<String, Deque<Cinematic>> undoSnapshots = new LinkedHashMap<>();
    private final Map<String, Deque<Cinematic>> redoSnapshots = new LinkedHashMap<>();

    public CinematicManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cinematics.clear();
        undoSnapshots.clear();
        redoSnapshots.clear();
        File scenesFolder = getScenesFolder();
        if (!scenesFolder.exists() && !scenesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create scenes folder at " + scenesFolder.getAbsolutePath());
            return;
        }

        boolean loadedFromFiles = false;
        File[] sceneFiles = scenesFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (sceneFiles != null) {
            for (File sceneFile : sceneFiles) {
                YamlConfiguration sceneConfig = YamlConfiguration.loadConfiguration(sceneFile);
                String fallbackId = stripExtension(sceneFile.getName());
                Cinematic cinematic = parseCinematic(sceneConfig, fallbackId);
                if (cinematic == null) {
                    continue;
                }
                cinematics.put(normalizeId(cinematic.getId()), cinematic);
                loadedFromFiles = true;
            }
        }

        if (loadedFromFiles) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection scenesSection = config.getConfigurationSection("cinematics");
        if (scenesSection == null) {
            return;
        }

        for (String id : scenesSection.getKeys(false)) {
            ConfigurationSection legacySceneSection = scenesSection.getConfigurationSection(id);
            if (legacySceneSection == null) {
                continue;
            }

            Cinematic cinematic = parseCinematic(legacySceneSection, id);
            if (cinematic == null) {
                continue;
            }
            cinematics.put(normalizeId(cinematic.getId()), cinematic);
        }

        if (!cinematics.isEmpty()) {
            save();
        }
    }

    public void save() {
        File scenesFolder = getScenesFolder();
        if (!scenesFolder.exists() && !scenesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create scenes folder at " + scenesFolder.getAbsolutePath());
            return;
        }

        File actorRecordingsFolder = getActorRecordingsFolder();
        if (!actorRecordingsFolder.exists() && !actorRecordingsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create actor recordings folder at " + actorRecordingsFolder.getAbsolutePath());
            return;
        }

        Set<String> expectedSceneFiles = new HashSet<>();
        Set<String> expectedActorRecordingFiles = new HashSet<>();

        for (Cinematic cinematic : cinematics.values()) {
            String normalizedSceneId = normalizeId(cinematic.getId());
            String sceneFileName = normalizedSceneId + ".yml";
            String actorRecordingFileName = normalizedSceneId + ".yml";
            expectedSceneFiles.add(sceneFileName);
            expectedActorRecordingFiles.add(actorRecordingFileName);

            YamlConfiguration sceneConfig = new YamlConfiguration();
            YamlConfiguration actorRecordingsConfig = new YamlConfiguration();
            writeCinematic(sceneConfig, cinematic);
            writeActorRecordings(actorRecordingsConfig, cinematic);

            try {
                atomicSaveYaml(sceneConfig, new File(scenesFolder, sceneFileName));
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save scene file " + sceneFileName + ": " + ex.getMessage());
            }

            try {
                atomicSaveYaml(actorRecordingsConfig, new File(actorRecordingsFolder, actorRecordingFileName));
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save actor recording file " + actorRecordingFileName + ": " + ex.getMessage());
            }
        }

        File[] existingSceneFiles = scenesFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (existingSceneFiles != null) {
            for (File existingSceneFile : existingSceneFiles) {
                if (expectedSceneFiles.contains(existingSceneFile.getName())) {
                    continue;
                }
                if (!existingSceneFile.delete()) {
                    plugin.getLogger().warning("Could not delete removed scene file " + existingSceneFile.getName());
                }
            }
        }

        File[] existingActorRecordingFiles = actorRecordingsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (existingActorRecordingFiles != null) {
            for (File existingActorRecordingFile : existingActorRecordingFiles) {
                if (expectedActorRecordingFiles.contains(existingActorRecordingFile.getName())) {
                    continue;
                }
                if (!existingActorRecordingFile.delete()) {
                    plugin.getLogger().warning("Could not delete removed actor recording file " + existingActorRecordingFile.getName());
                }
            }
        }

        FileConfiguration config = plugin.getConfig();
        config.set("cinematics", null);
        plugin.saveConfig();
    }

    private void atomicSaveYaml(YamlConfiguration sceneConfig, File destinationFile) throws IOException {
        File parent = destinationFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create parent directory for " + destinationFile.getName());
        }

        Path destinationPath = destinationFile.toPath();
        Path tempPath = destinationPath.resolveSibling(destinationPath.getFileName() + ".tmp");
        sceneConfig.save(tempPath.toFile());

        try {
            Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Cinematic parseCinematic(ConfigurationSection sceneSection, String fallbackId) {
        String id = sceneSection.getString("id", fallbackId);
        int durationTicks = Math.max(1, sceneSection.getInt("durationTicks", 200));
        List<CinematicPoint> points = new ArrayList<>();
        for (Map<?, ?> pointMap : sceneSection.getMapList("points")) {
            String worldName = String.valueOf(pointMap.get("world"));
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            double x = asDouble(pointMap.get("x"));
            double y = asDouble(pointMap.get("y"));
            double z = asDouble(pointMap.get("z"));
            float yaw = (float) asDouble(pointMap.get("yaw"));
            float pitch = (float) asDouble(pointMap.get("pitch"));
            Object tickObj = pointMap.containsKey("tick") ? pointMap.get("tick") : 0;
            int tick = Math.max(0, (int) asDouble(tickObj));
            CinematicPoint.InterpolationMode interpolationMode = CinematicPoint.InterpolationMode.fromString(
                    pointMap.containsKey("interpolation") ? String.valueOf(pointMap.get("interpolation")) : null);

            points.add(new CinematicPoint(tick, new Location(world, x, y, z, yaw, pitch), interpolationMode));
        }

        points.sort(Comparator.comparingInt(CinematicPoint::tick));
        List<String> startCommands = parseCommands(sceneSection.getStringList("startCommands"));
        List<String> endCommands = parseCommands(sceneSection.getStringList("endCommands"));
        Map<Integer, List<String>> tickCommands = parseTickCommands(sceneSection.getConfigurationSection("tickCommands"));
        Cinematic.EndAction endAction = parseEndAction(sceneSection.getConfigurationSection("endAction"));
        Map<String, List<ActorFrame>> actorFrames = loadActorFramesFile(normalizeId(id));
        Map<String, SceneActor> actors = parseActors(sceneSection.getConfigurationSection("actors"), actorFrames);
        boolean hidePlayersDuringPlayback = sceneSection.getBoolean("hidePlayersDuringPlayback", false);
        CinematicAudioTrack audioTrack = parseAudioTrack(sceneSection.getConfigurationSection("audio"));
        List<CinematicSubtitleCue> subtitleCues = parseSubtitles(sceneSection.getConfigurationSection("subtitles"));
        return new Cinematic(id, durationTicks, points, endAction, startCommands, endCommands, tickCommands, actors, hidePlayersDuringPlayback, audioTrack, subtitleCues);
    }

    private void writeCinematic(YamlConfiguration config, Cinematic cinematic) {
        config.set("id", cinematic.getId());
        List<Map<String, Object>> serializedPoints = new ArrayList<>();
        for (CinematicPoint point : cinematic.getPoints()) {
            Location loc = point.location();
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("tick", point.tick());
            serialized.put("world", loc.getWorld() == null ? "world" : loc.getWorld().getName());
            serialized.put("x", loc.getX());
            serialized.put("y", loc.getY());
            serialized.put("z", loc.getZ());
            serialized.put("yaw", loc.getYaw());
            serialized.put("pitch", loc.getPitch());
            serialized.put("interpolation", point.interpolationMode().name().toLowerCase(Locale.ROOT));
            serializedPoints.add(serialized);
        }

        config.set("durationTicks", cinematic.getDurationTicks());
        config.set("hidePlayersDuringPlayback", cinematic.shouldHidePlayersDuringPlayback());
        config.set("points", serializedPoints);
        config.set("startCommands", cinematic.getStartCommands());
        config.set("endCommands", cinematic.getEndCommands());
        config.set("tickCommands", null);
        for (Map.Entry<Integer, List<String>> entry : cinematic.getTickCommands().entrySet()) {
            config.set("tickCommands." + entry.getKey(), entry.getValue());
        }

        config.set("actors", null);
        for (SceneActor actor : cinematic.getActors().values()) {
            String actorPath = "actors." + actor.id();
            config.set(actorPath + ".displayName", actor.displayName());
            config.set(actorPath + ".scale", actor.scale());
            config.set(actorPath + ".skin.texture", actor.skinTexture());
            config.set(actorPath + ".skin.signature", actor.skinSignature());
            config.set(actorPath + ".appearAtTick", actor.appearAtTick());
            config.set(actorPath + ".disappearAtTick", actor.disappearAtTick());
        }

        config.set("audio", null);
        CinematicAudioTrack audioTrack = cinematic.getAudioTrack();
        if (audioTrack != null && audioTrack.isConfigured()) {
            config.set("audio.source", audioTrack.source());
            config.set("audio.track", audioTrack.track());
            config.set("audio.startAtMillis", audioTrack.startAtMillis());
            config.set("audio.playCommandTemplate", audioTrack.playCommandTemplate());
            config.set("audio.stopCommandTemplate", audioTrack.stopCommandTemplate());
        }

        config.set("subtitles", null);
        int subtitleIndex = 0;
        for (CinematicSubtitleCue cue : cinematic.getSubtitleCues()) {
            String path = "subtitles." + subtitleIndex++;
            config.set(path + ".startTick", cue.startTick());
            config.set(path + ".endTick", cue.endTick());
            config.set(path + ".line1", cue.line1());
            config.set(path + ".line2", cue.line2());
        }

        String endActionPath = "endAction";
        config.set(endActionPath + ".type", cinematic.getEndAction().type().name().toLowerCase(Locale.ROOT));
        GameMode gameMode = cinematic.getEndAction().gameMode();
        config.set(endActionPath + ".gameMode", gameMode == null ? null : gameMode.name().toLowerCase(Locale.ROOT));
        Location teleportLocation = cinematic.getEndAction().teleportLocation();
        if (teleportLocation == null || teleportLocation.getWorld() == null) {
            config.set(endActionPath + ".teleport", null);
            return;
        }

        config.set(endActionPath + ".teleport.world", teleportLocation.getWorld().getName());
        config.set(endActionPath + ".teleport.x", teleportLocation.getX());
        config.set(endActionPath + ".teleport.y", teleportLocation.getY());
        config.set(endActionPath + ".teleport.z", teleportLocation.getZ());
        config.set(endActionPath + ".teleport.yaw", teleportLocation.getYaw());
        config.set(endActionPath + ".teleport.pitch", teleportLocation.getPitch());
    }

    private File getScenesFolder() {
        return new File(plugin.getDataFolder(), "scenes");
    }

    private File getActorRecordingsFolder() {
        return new File(getScenesFolder(), "actor-recordings");
    }

    private Map<String, List<ActorFrame>> loadActorFramesFile(String sceneId) {
        File actorFile = new File(getActorRecordingsFolder(), sceneId + ".yml");
        if (!actorFile.exists()) {
            return Map.of();
        }

        YamlConfiguration actorConfig = YamlConfiguration.loadConfiguration(actorFile);
        ConfigurationSection actorsSection = actorConfig.getConfigurationSection("actors");
        if (actorsSection == null) {
            return Map.of();
        }

        Map<String, List<ActorFrame>> actorFrames = new LinkedHashMap<>();
        for (String actorId : actorsSection.getKeys(false)) {
            ConfigurationSection actorSection = actorsSection.getConfigurationSection(actorId);
            if (actorSection == null) {
                continue;
            }
            actorFrames.put(normalizeId(actorId), parseActorFrames(actorSection.getMapList("frames")));
        }
        return actorFrames;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    public boolean createCinematic(String id, int durationTicks) {
        String key = normalizeId(id);
        if (cinematics.containsKey(key)) {
            return false;
        }
        cinematics.put(key, new Cinematic(id, durationTicks, List.of(), Cinematic.EndAction.stayAtLastCameraPoint(), Map.of(), Map.of(), false, null, List.of()));
        save();
        return true;
    }

    public boolean deleteCinematic(String id) {
        String key = normalizeId(id);
        Cinematic removed = cinematics.remove(key);
        if (removed == null) {
            return false;
        }
        pushHistory(undoSnapshots, key, removed);
        redoSnapshots.remove(key);
        save();
        return true;
    }

    public Optional<Cinematic> getCinematic(String id) {
        return Optional.ofNullable(cinematics.get(normalizeId(id)));
    }

    public List<String> getCinematicIds() {
        return cinematics.values().stream().map(Cinematic::getId).toList();
    }

    public boolean setDuration(String id, int durationTicks) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), durationTicks, cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean upsertPoint(String id, int tick, Location location, CinematicPoint.InterpolationMode interpolationMode) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        List<CinematicPoint> updated = new ArrayList<>(cinematic.getPoints());
        updated.removeIf(p -> p.tick() == tick);
        updated.add(new CinematicPoint(Math.max(0, tick), location.clone(), interpolationMode));
        updated.sort(Comparator.comparingInt(CinematicPoint::tick));
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated, cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean deletePoint(String id, int tick) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        List<CinematicPoint> updated = new ArrayList<>(cinematic.getPoints());
        boolean removed = updated.removeIf(p -> p.tick() == tick);
        if (!removed) {
            return false;
        }

        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated, cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean setPointInterpolation(String id, int tick, CinematicPoint.InterpolationMode interpolationMode) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        List<CinematicPoint> updated = new ArrayList<>(cinematic.getPoints());
        boolean changed = false;

        for (int i = 0; i < updated.size(); i++) {
            CinematicPoint point = updated.get(i);
            if (point.tick() != tick) {
                continue;
            }

            updated.set(i, new CinematicPoint(point.tick(), point.location().clone(), interpolationMode));
            changed = true;
            break;
        }

        if (!changed) {
            return false;
        }

        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated, cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean clearPoints(String id) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), List.of(), cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean setEndAction(String id, Cinematic.EndAction endAction) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), endAction, cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean setHidePlayersDuringPlayback(String id, boolean hidePlayersDuringPlayback) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), hidePlayersDuringPlayback, cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean addTickCommand(String id, int tick, String command) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        String normalizedCommand = command == null ? "" : command.trim();
        if (normalizedCommand.isEmpty()) {
            return false;
        }

        Map<Integer, List<String>> updated = new TreeMap<>(cinematic.getTickCommands());
        List<String> commandsAtTick = new ArrayList<>(updated.getOrDefault(Math.max(0, tick), List.of()));
        commandsAtTick.add(normalizedCommand);
        updated.put(Math.max(0, tick), commandsAtTick);
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), updated, cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean removeTickCommand(String id, int tick, int index) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        int safeTick = Math.max(0, tick);
        Map<Integer, List<String>> updated = new TreeMap<>(cinematic.getTickCommands());
        List<String> commandsAtTick = new ArrayList<>(updated.getOrDefault(safeTick, List.of()));
        if (index < 1 || index > commandsAtTick.size()) {
            return false;
        }

        commandsAtTick.remove(index - 1);
        if (commandsAtTick.isEmpty()) {
            updated.remove(safeTick);
        } else {
            updated.put(safeTick, commandsAtTick);
        }

        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), updated, cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean clearTickCommands(String id, Integer tick) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        Map<Integer, List<String>> updated = new TreeMap<>(cinematic.getTickCommands());
        if (tick == null) {
            updated.clear();
        } else {
            updated.remove(Math.max(0, tick));
        }

        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), updated, cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean upsertActor(String sceneId, String actorId, String displayName, Double scale, String skinTexture, String skinSignature) {
        String key = normalizeId(sceneId);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        Map<String, SceneActor> updatedActors = new LinkedHashMap<>(cinematic.getActors());
        String actorKey = normalizeId(actorId);
        SceneActor current = updatedActors.get(actorKey);
        if (current == null) {
            current = new SceneActor(actorId, displayName == null ? actorId : displayName, skinTexture, skinSignature,
                    scale == null ? 1.0D : scale, 0, cinematic.getDurationTicks(), List.of());
        } else {
            current = current.withProfile(displayName, skinTexture, skinSignature, scale, null, null);
        }
        updatedActors.put(actorKey, current);
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), updatedActors, cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public SceneActor getActor(String sceneId, String actorId) {
        Cinematic cinematic = cinematics.get(normalizeId(sceneId));
        if (cinematic == null) {
            return null;
        }
        return cinematic.getActors().get(normalizeId(actorId));
    }

    public boolean saveActorFrames(String sceneId, String actorId, List<ActorFrame> frames) {
        String key = normalizeId(sceneId);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        Map<String, SceneActor> updatedActors = new LinkedHashMap<>(cinematic.getActors());
        String actorKey = normalizeId(actorId);
        SceneActor actor = updatedActors.get(actorKey);
        if (actor == null) {
            return false;
        }

        updatedActors.put(actorKey, actor.withFrames(frames));
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), updatedActors, cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean removeActor(String sceneId, String actorId) {
        String key = normalizeId(sceneId);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        Map<String, SceneActor> updatedActors = new LinkedHashMap<>(cinematic.getActors());
        if (updatedActors.remove(normalizeId(actorId)) == null) {
            return false;
        }

        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), updatedActors, cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }


    public boolean setActorWindow(String sceneId, String actorId, int appearAtTick, int disappearAtTick) {
        String key = normalizeId(sceneId);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        Map<String, SceneActor> updatedActors = new LinkedHashMap<>(cinematic.getActors());
        String actorKey = normalizeId(actorId);
        SceneActor actor = updatedActors.get(actorKey);
        if (actor == null) {
            return false;
        }

        updatedActors.put(actorKey, actor.withProfile(null, null, null, null, appearAtTick, disappearAtTick));
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), updatedActors, cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean setAudioTrack(String id, CinematicAudioTrack audioTrack) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), audioTrack, cinematic.getStartCommands(), cinematic.getEndCommands(), cinematic.getSubtitleCues()));
        save();
        return true;
    }

    public boolean upsertSubtitle(String id, CinematicSubtitleCue cue) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        List<CinematicSubtitleCue> updated = new ArrayList<>(cinematic.getSubtitleCues());
        updated.removeIf(existing -> existing.startTick() == cue.startTick());
        updated.add(cue);
        updated.sort(Comparator.comparingInt(CinematicSubtitleCue::startTick));
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), updated));
        save();
        return true;
    }

    public boolean removeSubtitle(String id, int startTick) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        List<CinematicSubtitleCue> updated = new ArrayList<>(cinematic.getSubtitleCues());
        boolean removed = updated.removeIf(existing -> existing.startTick() == Math.max(0, startTick));
        if (!removed) {
            return false;
        }
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), updated));
        save();
        return true;
    }

    public boolean clearSubtitles(String id) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        rememberUndoSnapshot(key, cinematic);
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands(), cinematic.getActors(), cinematic.shouldHidePlayersDuringPlayback(), cinematic.getAudioTrack(), cinematic.getStartCommands(), cinematic.getEndCommands(), List.of()));
        save();
        return true;
    }

    private CinematicAudioTrack parseAudioTrack(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String source = section.getString("source", "");
        String track = section.getString("track", "");
        int startAtMillis = Math.max(0, section.getInt("startAtMillis", 0));
        String playCommandTemplate = section.getString("playCommandTemplate", "oa play {player} {source}:{track} {\"startAtMillis\":{millis}}");
        String stopCommandTemplate = section.getString("stopCommandTemplate", "oa stop {player}");
        CinematicAudioTrack audioTrack = new CinematicAudioTrack(source, track, startAtMillis, playCommandTemplate, stopCommandTemplate);
        return audioTrack.isConfigured() ? audioTrack : null;
    }

    private List<CinematicSubtitleCue> parseSubtitles(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<CinematicSubtitleCue> cues = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection cueSection = section.getConfigurationSection(key);
            if (cueSection == null) {
                continue;
            }
            int startTick = Math.max(0, cueSection.getInt("startTick", 0));
            int endTick = Math.max(startTick, cueSection.getInt("endTick", startTick));
            String line1 = cueSection.getString("line1", "");
            String line2 = cueSection.getString("line2", "");
            cues.add(new CinematicSubtitleCue(startTick, endTick, line1, line2));
        }
        cues.sort(Comparator.comparingInt(CinematicSubtitleCue::startTick));
        return cues;
    }

    private Map<String, SceneActor> parseActors(ConfigurationSection section, Map<String, List<ActorFrame>> actorFrames) {
        if (section == null) {
            return Map.of();
        }

        Map<String, SceneActor> actors = new LinkedHashMap<>();
        for (String actorId : section.getKeys(false)) {
            ConfigurationSection actorSection = section.getConfigurationSection(actorId);
            if (actorSection == null) {
                continue;
            }

            String displayName = actorSection.getString("displayName", actorId);
            double scale = actorSection.getDouble("scale", 1.0D);
            int appearAt = Math.max(0, actorSection.getInt("appearAtTick", 0));
            int disappearAt = Math.max(appearAt, actorSection.getInt("disappearAtTick", Integer.MAX_VALUE));
            String texture = actorSection.getString("skin.texture");
            String signature = actorSection.getString("skin.signature");

            List<ActorFrame> frames = actorFrames.get(normalizeId(actorId));
            if (frames == null) {
                frames = parseActorFrames(actorSection.getMapList("frames"));
            }

            actors.put(normalizeId(actorId), new SceneActor(actorId, displayName, texture, signature, scale, appearAt, disappearAt, frames));
        }

        return actors;
    }

    private List<ActorFrame> parseActorFrames(List<Map<?, ?>> serializedFrames) {
        List<ActorFrame> frames = new ArrayList<>();
        for (Map<?, ?> frameMap : serializedFrames) {
            World world = Bukkit.getWorld(String.valueOf(frameMap.get("world")));
            if (world == null) {
                continue;
            }
            Object tickValue = frameMap.containsKey("tick") ? frameMap.get("tick") : 0;
            int tick = Math.max(0, (int) asDouble(tickValue));
            Location loc = new Location(
                    world,
                    asDouble(frameMap.get("x")),
                    asDouble(frameMap.get("y")),
                    asDouble(frameMap.get("z")),
                    (float) asDouble(frameMap.containsKey("yaw") ? frameMap.get("yaw") : 0),
                    (float) asDouble(frameMap.containsKey("pitch") ? frameMap.get("pitch") : 0));
            float headYaw = (float) asDouble(frameMap.containsKey("headYaw") ? frameMap.get("headYaw") : loc.getYaw());
            String pose = String.valueOf(frameMap.containsKey("pose") ? frameMap.get("pose") : "STANDING");
            frames.add(new ActorFrame(tick, loc, headYaw, pose));
        }
        return frames;
    }

    private void writeActorRecordings(YamlConfiguration config, Cinematic cinematic) {
        config.set("actors", null);
        for (SceneActor actor : cinematic.getActors().values()) {
            String actorPath = "actors." + actor.id();
            List<Map<String, Object>> frameList = new ArrayList<>();
            for (ActorFrame frame : actor.frames()) {
                Location frameLoc = frame.location();
                if (frameLoc == null || frameLoc.getWorld() == null) {
                    continue;
                }
                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("tick", frame.tick());
                serialized.put("world", frameLoc.getWorld().getName());
                serialized.put("x", frameLoc.getX());
                serialized.put("y", frameLoc.getY());
                serialized.put("z", frameLoc.getZ());
                serialized.put("yaw", frameLoc.getYaw());
                serialized.put("pitch", frameLoc.getPitch());
                serialized.put("headYaw", frame.headYaw());
                serialized.put("pose", frame.pose());
                frameList.add(serialized);
            }
            config.set(actorPath + ".frames", frameList);
        }
    }

    private Map<Integer, List<String>> parseTickCommands(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }

        Map<Integer, List<String>> commands = new TreeMap<>();
        for (String tickKey : section.getKeys(false)) {
            int tick;
            try {
                tick = Math.max(0, Integer.parseInt(tickKey));
            } catch (NumberFormatException ex) {
                continue;
            }

            List<String> values = section.getStringList(tickKey).stream()
                    .map(String::trim)
                    .filter(command -> !command.isBlank())
                    .toList();
            if (values.isEmpty()) {
                continue;
            }
            commands.put(tick, values);
        }

        return commands;
    }

    private List<String> parseCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        return commands.stream()
                .map(String::trim)
                .filter(command -> !command.isBlank())
                .toList();
    }

    private static Cinematic.EndAction parseEndAction(ConfigurationSection section) {
        if (section == null) {
            return Cinematic.EndAction.stayAtLastCameraPoint();
        }

        GameMode gameMode = parseEndGameMode(section.getString("gameMode"));
        Cinematic.EndActionType type = Cinematic.EndActionType.fromString(section.getString("type"));
        if (type != Cinematic.EndActionType.TELEPORT) {
            Cinematic.EndAction endAction = type == Cinematic.EndActionType.RETURN_TO_START
                    ? Cinematic.EndAction.returnToStart()
                    : Cinematic.EndAction.stayAtLastCameraPoint();
            return endAction.withGameMode(gameMode);
        }

        ConfigurationSection teleportSection = section.getConfigurationSection("teleport");
        if (teleportSection == null) {
            return Cinematic.EndAction.stayAtLastCameraPoint().withGameMode(gameMode);
        }

        World world = Bukkit.getWorld(teleportSection.getString("world", ""));
        if (world == null) {
            return Cinematic.EndAction.stayAtLastCameraPoint().withGameMode(gameMode);
        }

        double x = teleportSection.getDouble("x");
        double y = teleportSection.getDouble("y");
        double z = teleportSection.getDouble("z");
        float yaw = (float) teleportSection.getDouble("yaw");
        float pitch = (float) teleportSection.getDouble("pitch");
        return Cinematic.EndAction.teleportTo(new Location(world, x, y, z, yaw, pitch)).withGameMode(gameMode);
    }

    private static GameMode parseEndGameMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        for (GameMode gameMode : GameMode.values()) {
            if (gameMode.name().equalsIgnoreCase(rawValue)) {
                return gameMode;
            }
        }
        return null;
    }

    public boolean undo(String id) {
        String key = normalizeId(id);
        Cinematic current = cinematics.get(key);
        Deque<Cinematic> undoStack = undoSnapshots.get(key);
        if (current == null || undoStack == null || undoStack.isEmpty()) {
            return false;
        }
        Cinematic previous = undoStack.pop();

        pushHistory(redoSnapshots, key, current);
        cinematics.put(key, previous);
        if (undoStack.isEmpty()) {
            undoSnapshots.remove(key);
        }
        save();
        return true;
    }

    public boolean redo(String id) {
        String key = normalizeId(id);
        Cinematic current = cinematics.get(key);
        Deque<Cinematic> redoStack = redoSnapshots.get(key);
        if (current == null || redoStack == null || redoStack.isEmpty()) {
            return false;
        }
        Cinematic next = redoStack.pop();

        pushHistory(undoSnapshots, key, current);
        cinematics.put(key, next);
        if (redoStack.isEmpty()) {
            redoSnapshots.remove(key);
        }
        save();
        return true;
    }

    private void rememberUndoSnapshot(String key, Cinematic cinematic) {
        if (key == null || cinematic == null) {
            return;
        }
        Cinematic current = cinematics.get(key);
        if (current != null && areEquivalent(current, cinematic)) {
            return;
        }
        pushHistory(undoSnapshots, key, cinematic);
        redoSnapshots.remove(key);
    }

    private void pushHistory(Map<String, Deque<Cinematic>> history, String key, Cinematic cinematic) {
        Deque<Cinematic> stack = history.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        if (!stack.isEmpty() && areEquivalent(stack.peek(), cinematic)) {
            return;
        }
        stack.push(cinematic);
        while (stack.size() > HISTORY_LIMIT) {
            stack.removeLast();
        }
    }

    private boolean areEquivalent(Cinematic a, Cinematic b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getId().equalsIgnoreCase(b.getId())
                && a.getDurationTicks() == b.getDurationTicks()
                && a.getPoints().equals(b.getPoints())
                && a.getTickCommands().equals(b.getTickCommands())
                && a.getActors().equals(b.getActors())
                && a.shouldHidePlayersDuringPlayback() == b.shouldHidePlayersDuringPlayback()
                && java.util.Objects.equals(a.getAudioTrack(), b.getAudioTrack())
                && a.getSubtitleCues().equals(b.getSubtitleCues())
                && java.util.Objects.equals(a.getEndAction().type(), b.getEndAction().type())
                && java.util.Objects.equals(a.getEndAction().teleportLocation(), b.getEndAction().teleportLocation())
                && java.util.Objects.equals(a.getEndAction().gameMode(), b.getEndAction().gameMode());
    }

    private String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
