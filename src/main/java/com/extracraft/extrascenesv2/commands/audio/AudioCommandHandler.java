package com.extracraft.extrascenesv2.commands.audio;

import com.extracraft.extrascenesv2.cinematics.Cinematic;
import com.extracraft.extrascenesv2.cinematics.CinematicAudioTrack;
import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import java.util.Arrays;
import java.util.Locale;
import org.bukkit.command.CommandSender;

public final class AudioCommandHandler {

    private final CinematicManager manager;

    public AudioCommandHandler(CinematicManager manager) {
        this.manager = manager;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /scenes audio <set|clear|playtemplate|stoptemplate|show> <scene> ...");
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        String sceneId = args[2];

        if ("clear".equals(mode)) {
            if (!manager.setAudioTrack(sceneId, null)) {
                sender.sendMessage("§cThat scene does not exist.");
                return;
            }
            manager.save();
            sender.sendMessage("§aAudio track removed from scene " + sceneId + ".");
            return;
        }

        if ("show".equals(mode)) {
            Cinematic cinematic = manager.getCinematic(sceneId).orElse(null);
            if (cinematic == null) {
                sender.sendMessage("§cThat scene does not exist.");
                return;
            }
            CinematicAudioTrack track = cinematic.getAudioTrack();
            if (track == null || !track.isConfigured()) {
                sender.sendMessage("§eScene '" + sceneId + "' has no configured audio track.");
                return;
            }
            sender.sendMessage("§6Audio scene '" + sceneId + "': §b" + track.source() + ":" + track.track() + "§7 startAtMillis=" + track.startAtMillis());
            sender.sendMessage("§7playTemplate: " + track.playCommandTemplate());
            sender.sendMessage("§7stopTemplate: " + track.stopCommandTemplate());
            return;
        }

        if ("playtemplate".equals(mode) || "stoptemplate".equals(mode)) {
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /scenes audio " + mode + " <scene> <template...>");
                return;
            }

            Cinematic cinematic = manager.getCinematic(sceneId).orElse(null);
            if (cinematic == null) {
                sender.sendMessage("§cThat scene does not exist.");
                return;
            }

            CinematicAudioTrack currentTrack = cinematic.getAudioTrack();
            if (currentTrack == null || !currentTrack.isConfigured()) {
                sender.sendMessage("§cSet source/track first: /scenes audio set <scene> <source> <track> <startAtMillis>");
                return;
            }

            String templateInput = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            String normalizedTemplate = templateInput.equalsIgnoreCase("default") ? null : templateInput;

            CinematicAudioTrack updatedTrack = "playtemplate".equals(mode)
                    ? new CinematicAudioTrack(currentTrack.source(), currentTrack.track(), currentTrack.startAtMillis(), normalizedTemplate, currentTrack.stopCommandTemplate())
                    : new CinematicAudioTrack(currentTrack.source(), currentTrack.track(), currentTrack.startAtMillis(), currentTrack.playCommandTemplate(), normalizedTemplate);

            if (!manager.setAudioTrack(sceneId, updatedTrack)) {
                sender.sendMessage("§cCould not update audio template.");
                return;
            }

            manager.save();
            sender.sendMessage("§a" + ("playtemplate".equals(mode) ? "Play" : "Stop") + " template updated for scene '" + sceneId + "'.");
            return;
        }

        if (!"set".equals(mode) || args.length < 6) {
            sender.sendMessage("§cUsage: /scenes audio set <scene> <source> <track> <startAtMillis>");
            return;
        }

        int startAtMillis;
        try {
            startAtMillis = Math.max(0, Integer.parseInt(args[5]));
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cInvalid startAtMillis.");
            return;
        }

        Cinematic existing = manager.getCinematic(sceneId).orElse(null);
        String playTemplate = existing == null || existing.getAudioTrack() == null ? null : existing.getAudioTrack().playCommandTemplate();
        String stopTemplate = existing == null || existing.getAudioTrack() == null ? null : existing.getAudioTrack().stopCommandTemplate();

        CinematicAudioTrack track = new CinematicAudioTrack(args[3], args[4], startAtMillis, playTemplate, stopTemplate);
        if (!manager.setAudioTrack(sceneId, track)) {
            sender.sendMessage("§cThat scene does not exist.");
            return;
        }

        manager.save();
        sender.sendMessage("§aAudio track configured for scene '" + sceneId + "'.");
    }
}
