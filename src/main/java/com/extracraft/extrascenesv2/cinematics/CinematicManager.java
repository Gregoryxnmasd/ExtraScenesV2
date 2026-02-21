package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CinematicManager {

    private final JavaPlugin plugin;
    private final Map<String, Cinematic> cinematics = new LinkedHashMap<>();

    public CinematicManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cinematics.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection scenesSection = config.getConfigurationSection("cinematics");
        if (scenesSection == null) {
            return;
        }

        for (String id : scenesSection.getKeys(false)) {
            int durationTicks = Math.max(1, scenesSection.getInt(id + ".durationTicks", 200));
            List<CinematicPoint> points = new ArrayList<>();
            List<Map<?, ?>> rawPoints = scenesSection.getMapList(id + ".points");
            for (Map<?, ?> pointMap : rawPoints) {
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

            Map<Integer, List<String>> tickCommands = parseTickCommands(
                    scenesSection.getConfigurationSection(id + ".tickCommands"));

            ConfigurationSection endActionSection = scenesSection.getConfigurationSection(id + ".endAction");
            Cinematic.EndAction endAction = parseEndAction(endActionSection);
            cinematics.put(normalizeId(id), new Cinematic(id, durationTicks, points, endAction, tickCommands));
        }
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();
        config.set("cinematics", null);

        for (Cinematic cinematic : cinematics.values()) {
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
            config.set("cinematics." + cinematic.getId() + ".durationTicks", cinematic.getDurationTicks());
            config.set("cinematics." + cinematic.getId() + ".points", serializedPoints);
            String tickCommandPath = "cinematics." + cinematic.getId() + ".tickCommands";
            config.set(tickCommandPath, null);
            for (Map.Entry<Integer, List<String>> entry : cinematic.getTickCommands().entrySet()) {
                config.set(tickCommandPath + "." + entry.getKey(), entry.getValue());
            }

            String endActionPath = "cinematics." + cinematic.getId() + ".endAction";
            config.set(endActionPath + ".type", cinematic.getEndAction().type().name().toLowerCase(Locale.ROOT));
            Location teleportLocation = cinematic.getEndAction().teleportLocation();
            if (teleportLocation == null || teleportLocation.getWorld() == null) {
                config.set(endActionPath + ".teleport", null);
                continue;
            }

            config.set(endActionPath + ".teleport.world", teleportLocation.getWorld().getName());
            config.set(endActionPath + ".teleport.x", teleportLocation.getX());
            config.set(endActionPath + ".teleport.y", teleportLocation.getY());
            config.set(endActionPath + ".teleport.z", teleportLocation.getZ());
            config.set(endActionPath + ".teleport.yaw", teleportLocation.getYaw());
            config.set(endActionPath + ".teleport.pitch", teleportLocation.getPitch());
        }

        plugin.saveConfig();
    }

    public boolean createCinematic(String id, int durationTicks) {
        String key = normalizeId(id);
        if (cinematics.containsKey(key)) {
            return false;
        }
        cinematics.put(key, new Cinematic(id, durationTicks, List.of(), Cinematic.EndAction.stayAtLastCameraPoint(), Map.of()));
        return true;
    }

    public boolean deleteCinematic(String id) {
        return cinematics.remove(normalizeId(id)) != null;
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
        cinematics.put(key, new Cinematic(cinematic.getId(), durationTicks, cinematic.getPoints(), cinematic.getEndAction(), cinematic.getTickCommands()));
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
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated, cinematic.getEndAction(), cinematic.getTickCommands()));
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

        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated, cinematic.getEndAction(), cinematic.getTickCommands()));
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

        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated, cinematic.getEndAction(), cinematic.getTickCommands()));
        return true;
    }

    public boolean clearPoints(String id) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), List.of(), cinematic.getEndAction(), cinematic.getTickCommands()));
        return true;
    }

    public boolean setEndAction(String id, Cinematic.EndAction endAction) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), endAction, cinematic.getTickCommands()));
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
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), updated));
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

        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), updated));
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

        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), cinematic.getPoints(), cinematic.getEndAction(), updated));
        return true;
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

    private static Cinematic.EndAction parseEndAction(ConfigurationSection section) {
        if (section == null) {
            return Cinematic.EndAction.stayAtLastCameraPoint();
        }

        Cinematic.EndActionType type = Cinematic.EndActionType.fromString(section.getString("type"));
        if (type != Cinematic.EndActionType.TELEPORT) {
            return type == Cinematic.EndActionType.RETURN_TO_START
                    ? Cinematic.EndAction.returnToStart()
                    : Cinematic.EndAction.stayAtLastCameraPoint();
        }

        ConfigurationSection teleportSection = section.getConfigurationSection("teleport");
        if (teleportSection == null) {
            return Cinematic.EndAction.stayAtLastCameraPoint();
        }

        World world = Bukkit.getWorld(teleportSection.getString("world", ""));
        if (world == null) {
            return Cinematic.EndAction.stayAtLastCameraPoint();
        }

        double x = teleportSection.getDouble("x");
        double y = teleportSection.getDouble("y");
        double z = teleportSection.getDouble("z");
        float yaw = (float) teleportSection.getDouble("yaw");
        float pitch = (float) teleportSection.getDouble("pitch");
        return Cinematic.EndAction.teleportTo(new Location(world, x, y, z, yaw, pitch));
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
