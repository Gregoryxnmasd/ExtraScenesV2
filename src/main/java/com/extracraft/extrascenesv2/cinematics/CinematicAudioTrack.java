package com.extracraft.extrascenesv2.cinematics;

public record CinematicAudioTrack(String source, String track, int startAtMillis, String stopCommandTemplate) {

    public CinematicAudioTrack {
        source = source == null ? "" : source.trim();
        track = track == null ? "" : track.trim();
        startAtMillis = Math.max(0, startAtMillis);
        stopCommandTemplate = stopCommandTemplate == null || stopCommandTemplate.isBlank()
                ? "oa stop {player}"
                : stopCommandTemplate.trim();
    }

    public boolean isConfigured() {
        return !source.isBlank() && !track.isBlank();
    }
}
