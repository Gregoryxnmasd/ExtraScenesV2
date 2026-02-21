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
    private final List<ActorFrame> frames;

    public SceneActor(String id, String displayName, String skinTexture, String skinSignature, double scale, List<ActorFrame> frames) {
        this.id = id;
        this.displayName = displayName == null ? id : displayName;
        this.skinTexture = blankToNull(skinTexture);
        this.skinSignature = blankToNull(skinSignature);
        this.scale = Math.max(0.0625D, Math.min(16.0D, scale));
        List<ActorFrame> copied = new ArrayList<>(frames == null ? List.of() : frames);
        copied.sort(Comparator.comparingInt(ActorFrame::tick));
        this.frames = Collections.unmodifiableList(copied);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String skinTexture() {
        return skinTexture;
    }

    public String skinSignature() {
        return skinSignature;
    }

    public double scale() {
        return scale;
    }

    public List<ActorFrame> frames() {
        return frames;
    }

    public SceneActor withFrames(List<ActorFrame> updatedFrames) {
        return new SceneActor(id, displayName, skinTexture, skinSignature, scale, updatedFrames);
    }

    public SceneActor withProfile(String updatedName, String updatedTexture, String updatedSignature, Double updatedScale) {
        return new SceneActor(
                id,
                updatedName == null ? displayName : updatedName,
                updatedTexture == null ? skinTexture : updatedTexture,
                updatedSignature == null ? skinSignature : updatedSignature,
                updatedScale == null ? scale : updatedScale,
                frames);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}

