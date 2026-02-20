package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

                PointInterpolation interpolation = PointInterpolation.fromString(String.valueOf(pointMap.get("interpolation")));
                points.add(new CinematicPoint(tick, new Location(world, x, y, z, yaw, pitch), interpolation));
            }

            points.sort(Comparator.comparingInt(CinematicPoint::tick));
            cinematics.put(normalizeId(id), new Cinematic(id, durationTicks, points));
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
                serialized.put("interpolation", point.interpolation().name().toLowerCase(Locale.ROOT));
                serializedPoints.add(serialized);
            }
            config.set("cinematics." + cinematic.getId() + ".durationTicks", cinematic.getDurationTicks());
            config.set("cinematics." + cinematic.getId() + ".points", serializedPoints);
        }

        plugin.saveConfig();
    }

    public boolean createCinematic(String id, int durationTicks) {
        String key = normalizeId(id);
        if (cinematics.containsKey(key)) {
            return false;
        }
        cinematics.put(key, new Cinematic(id, durationTicks, List.of()));
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
        cinematics.put(key, new Cinematic(cinematic.getId(), durationTicks, cinematic.getPoints()));
        return true;
    }

    public boolean upsertPoint(String id, int tick, Location location) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        List<CinematicPoint> updated = new ArrayList<>(cinematic.getPoints());
        updated.removeIf(p -> p.tick() == tick);
        updated.add(new CinematicPoint(Math.max(0, tick), location.clone(), PointInterpolation.SMOOTH));
        updated.sort(Comparator.comparingInt(CinematicPoint::tick));
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated));
        return true;
    }

    public boolean setPointInterpolation(String id, int tick, PointInterpolation interpolation) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        List<CinematicPoint> updated = new ArrayList<>(cinematic.getPoints());
        for (int i = 0; i < updated.size(); i++) {
            CinematicPoint point = updated.get(i);
            if (point.tick() == tick) {
                updated.set(i, new CinematicPoint(point.tick(), point.location().clone(), interpolation));
                cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated));
                return true;
            }
        }

        return false;
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

        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), updated));
        return true;
    }

    public boolean clearPoints(String id) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }
        cinematics.put(key, new Cinematic(cinematic.getId(), cinematic.getDurationTicks(), List.of()));
        return true;
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
