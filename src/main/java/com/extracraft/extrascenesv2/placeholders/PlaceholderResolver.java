package com.extracraft.extrascenesv2.placeholders;

import com.extracraft.extrascenesv2.cinematics.Cinematic;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlaceholderResolver {

    public String apply(String input, Player player, Cinematic cinematic, int tick) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String output = input;
        for (Map.Entry<String, String> entry : buildPlaceholders(player, cinematic, tick).entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    private Map<String, String> buildPlaceholders(Player player, Cinematic cinematic, int tick) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("player_display_name", player.getDisplayName());
        values.put("player_uuid", player.getUniqueId().toString());
        values.put("tick", String.valueOf(Math.max(0, tick)));
        values.put("scene", cinematic.getId());

        Location location = player.getLocation();
        values.put("world", location.getWorld() == null ? "" : location.getWorld().getName());
        values.put("x", String.format(Locale.US, "%.3f", location.getX()));
        values.put("y", String.format(Locale.US, "%.3f", location.getY()));
        values.put("z", String.format(Locale.US, "%.3f", location.getZ()));
        values.put("yaw", String.format(Locale.US, "%.2f", location.getYaw()));
        values.put("pitch", String.format(Locale.US, "%.2f", location.getPitch()));
        return values;
    }
}
