package com.extracraft.extrascenesv2.cinematics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SceneActor {

    private final String id;
    private final String displayName;
    private final String skinTexture;
    private final String skinSignature;
    private final double scale;
    private final int appearAtTick;
    private final int disappearAtTick;
    private final List<ActorFrame> frames;

    public SceneActor(String id, String displayName, String skinTexture, String skinSignature,
                      double scale, int appearAtTick, int disappearAtTick, List<ActorFrame> frames) {
        this.id = id;
        this.displayName = displayName == null ? id : displayName;
        this.skinTexture = blankToNull(skinTexture);
        this.skinSignature = blankToNull(skinSignature);
        this.scale = Math.max(0.0625D, Math.min(16.0D, scale));
        this.appearAtTick = Math.max(0, appearAtTick);
        this.disappearAtTick = Math.max(this.appearAtTick, disappearAtTick);
        List<ActorFrame> copied = new ArrayList<>(frames == null ? List.of() : frames);
        copied.sort(Comparator.comparingInt(ActorFrame::tick));
        this.frames = Collections.unmodifiableList(copied);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String skinTexture() { return skinTexture; }
    public String skinSignature() { return skinSignature; }
    public double scale() { return scale; }
    public int appearAtTick() { return appearAtTick; }
    public int disappearAtTick() { return disappearAtTick; }
    public List<ActorFrame> frames() { return frames; }

    public boolean isVisibleAtTick(int tick) {
        return tick >= appearAtTick && tick <= disappearAtTick;
    }

    public SceneActor withFrames(List<ActorFrame> updatedFrames) {
        return new SceneActor(id, displayName, skinTexture, skinSignature, scale, appearAtTick, disappearAtTick, updatedFrames);
    }

    public SceneActor withProfile(String updatedName, String updatedTexture, String updatedSignature, Double updatedScale,
                                  Integer updatedAppearAtTick, Integer updatedDisappearAtTick) {
        return new SceneActor(
                id,
                updatedName == null ? displayName : updatedName,
                updatedTexture == null ? skinTexture : updatedTexture,
                updatedSignature == null ? skinSignature : updatedSignature,
                updatedScale == null ? scale : updatedScale,
                updatedAppearAtTick == null ? appearAtTick : updatedAppearAtTick,
                updatedDisappearAtTick == null ? disappearAtTick : updatedDisappearAtTick,
                frames);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
