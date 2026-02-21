package com.extracraft.extrascenesv2.cinematics;

import org.bukkit.Location;

public record ActorFrame(int tick, Location location) {

    public ActorFrame {
        tick = Math.max(0, tick);
        location = location == null ? null : location.clone();
    }

    @Override
    public Location location() {
        return location == null ? null : location.clone();
    }
}

