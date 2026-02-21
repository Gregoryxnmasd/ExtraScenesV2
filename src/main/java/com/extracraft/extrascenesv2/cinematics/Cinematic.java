package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Location;

public final class Cinematic {

    private final String id;
    private final int durationTicks;
    private final List<CinematicPoint> points;
    private final EndAction endAction;

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points) {
        this(id, durationTicks, points, EndAction.stayAtLastCameraPoint());
    }

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points, EndAction endAction) {
        this.id = id;
        this.durationTicks = Math.max(1, durationTicks);
        this.points = new ArrayList<>(points);
        this.endAction = endAction == null ? EndAction.stayAtLastCameraPoint() : endAction;
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

    public EndAction getEndAction() {
        return endAction;
    }

    public enum EndActionType {
        RETURN_TO_START,
        TELEPORT,
        STAY_AT_LAST_CAMERA_POINT;

        public static EndActionType fromString(String value) {
            if (value == null) {
                return STAY_AT_LAST_CAMERA_POINT;
            }

            for (EndActionType type : values()) {
                if (type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }

            return STAY_AT_LAST_CAMERA_POINT;
        }
    }

    public static final class EndAction {
        private final EndActionType type;
        private final Location teleportLocation;

        private EndAction(EndActionType type, Location teleportLocation) {
            this.type = type;
            this.teleportLocation = teleportLocation == null ? null : teleportLocation.clone();
        }

        public static EndAction returnToStart() {
            return new EndAction(EndActionType.RETURN_TO_START, null);
        }

        public static EndAction stayAtLastCameraPoint() {
            return new EndAction(EndActionType.STAY_AT_LAST_CAMERA_POINT, null);
        }

        public static EndAction teleportTo(Location teleportLocation) {
            return new EndAction(EndActionType.TELEPORT, teleportLocation);
        }

        public EndActionType type() {
            return type;
        }

        public Location teleportLocation() {
            return teleportLocation == null ? null : teleportLocation.clone();
        }
    }
}
