package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
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
                String modeRaw = pointMap.containsKey("mode") ? String.valueOf(pointMap.get("mode")) : "FREE";
                Object durationObj = pointMap.containsKey("duration") ? pointMap.get("duration") : 60;
                int duration = Math.max(1, (int) asDouble(durationObj));

                CinematicPoint.CameraMode mode;
                try {
                    mode = CinematicPoint.CameraMode.valueOf(modeRaw.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    mode = CinematicPoint.CameraMode.FREE;
                }

                points.add(new CinematicPoint(new Location(world, x, y, z, yaw, pitch), mode, duration));
            }

            cinematics.put(normalizeId(id), new Cinematic(id, points));
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
                serialized.put("world", loc.getWorld() == null ? "world" : loc.getWorld().getName());
                serialized.put("x", loc.getX());
                serialized.put("y", loc.getY());
                serialized.put("z", loc.getZ());
                serialized.put("yaw", loc.getYaw());
                serialized.put("pitch", loc.getPitch());
                serialized.put("mode", point.cameraMode().name());
                serialized.put("duration", point.durationTicks());
                serializedPoints.add(serialized);
            }
            config.set("cinematics." + cinematic.getId() + ".points", serializedPoints);
        }

        plugin.saveConfig();
    }

    public boolean createCinematic(String id) {
        String key = normalizeId(id);
        if (cinematics.containsKey(key)) {
            return false;
        }
        cinematics.put(key, new Cinematic(id, List.of()));
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

    public boolean addPoint(String id, CinematicPoint point) {
        String key = normalizeId(id);
        Cinematic cinematic = cinematics.get(key);
        if (cinematic == null) {
            return false;
        }

        List<CinematicPoint> updated = new ArrayList<>(cinematic.getPoints());
        updated.add(point);
        cinematics.put(key, new Cinematic(cinematic.getId(), updated));
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
