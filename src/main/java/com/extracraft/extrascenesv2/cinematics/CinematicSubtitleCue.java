package com.extracraft.extrascenesv2.cinematics;

public record CinematicSubtitleCue(int startTick, int endTick, String line1, String line2) {

    public CinematicSubtitleCue {
        startTick = Math.max(0, startTick);
        endTick = Math.max(startTick, endTick);
        line1 = line1 == null ? "" : line1;
        line2 = line2 == null ? "" : line2;
    }

    public boolean matchesTick(int tick) {
        return tick >= startTick && tick <= endTick;
    }
}
