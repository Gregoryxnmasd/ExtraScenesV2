package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Cinematic {

    private final String id;
    private final int durationTicks;
    private final List<CinematicPoint> points;

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points) {
        this.id = id;
        this.durationTicks = Math.max(1, durationTicks);
        this.points = new ArrayList<>(points);
    }

    public String getId() {
        return id;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public List<CinematicPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
