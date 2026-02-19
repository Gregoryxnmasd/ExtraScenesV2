package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Cinematic {

    private final String id;
    private final List<CinematicPoint> points;

    public Cinematic(String id, List<CinematicPoint> points) {
        this.id = id;
        this.points = new ArrayList<>(points);
    }

    public String getId() {
        return id;
    }

    public List<CinematicPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
