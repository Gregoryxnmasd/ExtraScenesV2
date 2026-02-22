package com.extracraft.extrascenesv2.cinematics;

import java.util.Locale;
import org.bukkit.Location;

public record ActorFrame(int tick, Location location, float headYaw, String pose) {

    public ActorFrame(int tick, Location location) {
        this(tick, location, location == null ? 0.0F : location.getYaw(), "STANDING");
    }

    public ActorFrame {
        tick = Math.max(0, tick);
        location = location == null ? null : location.clone();
        pose = pose == null || pose.isBlank() ? "STANDING" : pose.toUpperCase(Locale.ROOT);
    }

    @Override
    public Location location() {
        return location == null ? null : location.clone();
    }
}
