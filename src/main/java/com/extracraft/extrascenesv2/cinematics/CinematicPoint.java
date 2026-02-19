package com.extracraft.extrascenesv2.cinematics;

import org.bukkit.Location;

public record CinematicPoint(Location location, CameraMode cameraMode, int durationTicks) {

    public enum CameraMode {
        LOCKED,
        FREE
    }
}
