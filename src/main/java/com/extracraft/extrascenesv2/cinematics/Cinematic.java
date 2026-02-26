package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;

public final class Cinematic {

    private final String id;
    private final int durationTicks;
    private final List<CinematicPoint> points;
    private final EndAction endAction;
    private final List<String> startCommands;
    private final List<String> endCommands;
    private final Map<Integer, List<String>> tickCommands;
    private final Map<String, SceneActor> actors;
    private final boolean hidePlayersDuringPlayback;
    private final CinematicAudioTrack audioTrack;
    private final List<CinematicSubtitleCue> subtitleCues;

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points) {
        this(id, durationTicks, points, EndAction.stayAtLastCameraPoint(), Map.of(), Map.of(), false, null, List.of());
    }

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points, EndAction endAction) {
        this(id, durationTicks, points, endAction, Map.of(), Map.of(), false, null, List.of());
    }

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points, EndAction endAction,
                     Map<Integer, List<String>> tickCommands, Map<String, SceneActor> actors) {
        this(id, durationTicks, points, endAction, tickCommands, actors, false, null, List.of());
    }

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points, EndAction endAction,
                     Map<Integer, List<String>> tickCommands, Map<String, SceneActor> actors,
                     boolean hidePlayersDuringPlayback, CinematicAudioTrack audioTrack,
                     List<CinematicSubtitleCue> subtitleCues) {
        this(id, durationTicks, points, endAction, List.of(), List.of(), tickCommands, actors,
                hidePlayersDuringPlayback, audioTrack, subtitleCues);
    }

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points, EndAction endAction,
                     Map<Integer, List<String>> tickCommands, Map<String, SceneActor> actors,
                     boolean hidePlayersDuringPlayback, CinematicAudioTrack audioTrack,
                     List<String> startCommands, List<String> endCommands,
                     List<CinematicSubtitleCue> subtitleCues) {
        this(id, durationTicks, points, endAction, startCommands, endCommands, tickCommands, actors,
                hidePlayersDuringPlayback, audioTrack, subtitleCues);
    }

    public Cinematic(String id, int durationTicks, List<CinematicPoint> points, EndAction endAction,
                     List<String> startCommands, List<String> endCommands,
                     Map<Integer, List<String>> tickCommands, Map<String, SceneActor> actors,
                     boolean hidePlayersDuringPlayback, CinematicAudioTrack audioTrack,
                     List<CinematicSubtitleCue> subtitleCues) {
        this.id = id;
        this.durationTicks = Math.max(1, durationTicks);
        this.points = new ArrayList<>(points);
        this.endAction = endAction == null ? EndAction.stayAtLastCameraPoint() : endAction;
        this.startCommands = sanitizeCommands(startCommands);
        this.endCommands = sanitizeCommands(endCommands);
        this.tickCommands = deepCopyTickCommands(tickCommands);
        this.actors = deepCopyActors(actors);
        this.hidePlayersDuringPlayback = hidePlayersDuringPlayback;
        this.audioTrack = audioTrack;
        this.subtitleCues = subtitleCues == null ? List.of() : subtitleCues.stream()
                .sorted(java.util.Comparator.comparingInt(CinematicSubtitleCue::startTick))
                .toList();
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

    public Map<Integer, List<String>> getTickCommands() {
        return tickCommands;
    }

    public List<String> getStartCommands() {
        return startCommands;
    }

    public List<String> getEndCommands() {
        return endCommands;
    }

    public Map<String, SceneActor> getActors() {
        return actors;
    }

    public boolean shouldHidePlayersDuringPlayback() {
        return hidePlayersDuringPlayback;
    }

    public CinematicAudioTrack getAudioTrack() {
        return audioTrack;
    }

    public List<CinematicSubtitleCue> getSubtitleCues() {
        return subtitleCues;
    }

    public CinematicSubtitleCue getSubtitleAtTick(int tick) {
        for (CinematicSubtitleCue cue : subtitleCues) {
            if (cue.matchesTick(tick)) {
                return cue;
            }
        }
        return null;
    }


    private Map<String, SceneActor> deepCopyActors(Map<String, SceneActor> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, SceneActor> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SceneActor> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey().toLowerCase(java.util.Locale.ROOT), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private List<String> sanitizeCommands(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        return source.stream()
                .filter(command -> command != null && !command.isBlank())
                .map(String::trim)
                .toList();
    }

    private Map<Integer, List<String>> deepCopyTickCommands(Map<Integer, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> entry : source.entrySet()) {
            int tick = Math.max(0, entry.getKey());
            List<String> commands = entry.getValue() == null ? List.of() : entry.getValue().stream()
                    .filter(command -> command != null && !command.isBlank())
                    .map(String::trim)
                    .toList();
            if (commands.isEmpty()) {
                continue;
            }
            copy.put(tick, List.copyOf(commands));
        }

        return Collections.unmodifiableMap(copy);
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
