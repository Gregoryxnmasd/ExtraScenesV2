package com.extracraft.extrascenesv2.cinematics;

public record CinematicAudioTrack(String source, String track, int startAtMillis,
                                  String playCommandTemplate, String stopCommandTemplate) {

    private static final String DEFAULT_PLAY_TEMPLATE = "oa play {player} {source}:{track} {\"startAtMillis\":{millis}}";
    private static final String DEFAULT_STOP_TEMPLATE = "oa stop {player}";

    public CinematicAudioTrack {
        source = source == null ? "" : source.trim();
        track = track == null ? "" : track.trim();
        startAtMillis = Math.max(0, startAtMillis);
        playCommandTemplate = normalizeTemplate(playCommandTemplate, DEFAULT_PLAY_TEMPLATE);
        stopCommandTemplate = normalizeTemplate(stopCommandTemplate, DEFAULT_STOP_TEMPLATE);
    }

    public boolean isConfigured() {
        return !source.isBlank() && !track.isBlank();
    }

    public String renderPlayCommand(String playerName, int millis) {
        return applyCommonPlaceholders(playCommandTemplate, playerName, millis);
    }

    public String renderStopCommand(String playerName) {
        return applyCommonPlaceholders(stopCommandTemplate, playerName, 0);
    }

    private String applyCommonPlaceholders(String template, String playerName, int millis) {
        String command = template
                .replace("{player}", playerName == null ? "" : playerName)
                .replace("{source}", source)
                .replace("{track}", track)
                .replace("{millis}", String.valueOf(Math.max(0, millis)));
        if (command.startsWith("/")) {
            return command.substring(1);
        }
        return command;
    }

    private static String normalizeTemplate(String rawTemplate, String fallback) {
        if (rawTemplate == null || rawTemplate.isBlank()) {
            return fallback;
        }
        String normalized = rawTemplate.trim();
        if (normalized.startsWith("/")) {
            return normalized.substring(1);
        }
        return normalized;
    }
}
