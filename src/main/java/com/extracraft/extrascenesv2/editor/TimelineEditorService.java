package com.extracraft.extrascenesv2.editor;

import com.extracraft.extrascenesv2.cinematics.Cinematic;
import com.extracraft.extrascenesv2.cinematics.CinematicManager;
import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TimelineEditorService {

    private static final String C_GREEN = "§a";
    private static final String C_YELLOW = "§e";
    private static final String C_GRAY = "§7";

    private final CinematicManager manager;
    private final CinematicPlaybackService playbackService;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private static final int[] EDITOR_SLOTS = {0, 1, 2, 4, 6, 7, 8};

    public TimelineEditorService(CinematicManager manager, CinematicPlaybackService playbackService) {
        this.manager = manager;
        this.playbackService = playbackService;
    }

    public boolean open(Player player, String sceneId, int startTick) {
        Cinematic cinematic = manager.getCinematic(sceneId).orElse(null);
        if (cinematic == null) {
            return false;
        }

        close(player);
        int safeTick = Math.max(0, Math.min(startTick, cinematic.getDurationTicks()));
        if (!playbackService.play(player, cinematic, safeTick, cinematic.getDurationTicks())) {
            return false;
        }
        playbackService.pause(player);

        sessions.put(player.getUniqueId(), new EditorSession(cinematic.getId(), safeTick));
        giveEditorHotbar(player);
        player.sendMessage(C_GREEN + "Editor abierto para '" + cinematic.getId() + "' en tick " + safeTick + ".");
        player.sendMessage(C_YELLOW + "Hotbar timeline: -20 -5 -1 | play/pause | +1 +5 +20");
        return true;
    }

    public boolean close(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        playbackService.stop(player);
        restoreOriginalHotbar(player, session);
        player.sendMessage(C_GREEN + "Editor cerrado para escena '" + session.sceneId + "'.");
        return true;
    }

    public boolean togglePlayPause(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }

        if (playbackService.pause(player)) {
            session.playing = false;
            player.sendActionBar(Component.text(C_YELLOW + "Pausado en tick " + playbackService.getCurrentTick(player.getUniqueId())));
            return true;
        }

        if (playbackService.resume(player)) {
            session.playing = true;
            player.sendActionBar(Component.text(C_GREEN + "Reproduciendo timeline..."));
            return true;
        }
        return false;
    }

    public boolean seek(Player player, int deltaTicks) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }

        int currentTick = playbackService.getCurrentTick(player.getUniqueId());
        int targetTick = Math.max(0, currentTick + deltaTicks);
        if (!playbackService.seek(player, targetTick)) {
            return false;
        }

        session.tick = playbackService.getCurrentTick(player.getUniqueId());
        player.sendActionBar(Component.text(C_GRAY + "Tick " + session.tick));
        return true;
    }

    public boolean seekTo(Player player, int tick) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (!playbackService.seek(player, tick)) {
            return false;
        }
        session.tick = playbackService.getCurrentTick(player.getUniqueId());
        player.sendActionBar(Component.text(C_GRAY + "Tick " + session.tick));
        return true;
    }

    public String getSceneId(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        return session == null ? null : session.sceneId;
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean isEditorSlot(int slot) {
        for (int editorSlot : EDITOR_SLOTS) {
            if (editorSlot == slot) {
                return true;
            }
        }
        return false;
    }

    public boolean isEditorItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return false;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return plain.startsWith("[Timeline]");
    }

    public void handleHotbarAction(Player player, int slot) {
        if (!hasSession(player)) {
            return;
        }

        switch (slot) {
            case 0 -> seek(player, -20);
            case 1 -> seek(player, -5);
            case 2 -> seek(player, -1);
            case 4 -> togglePlayPause(player);
            case 6 -> seek(player, 1);
            case 7 -> seek(player, 5);
            case 8 -> seek(player, 20);
            default -> {
            }
        }
    }

    public void handleQuit(Player player) {
        close(player);
    }

    private void giveEditorHotbar(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        for (int slot : EDITOR_SLOTS) {
            session.originalHotbar.put(slot, cloneIfPresent(player.getInventory().getItem(slot)));
        }

        player.getInventory().setItem(0, named(Material.RED_STAINED_GLASS_PANE, "[Timeline] -20t"));
        player.getInventory().setItem(1, named(Material.ORANGE_STAINED_GLASS_PANE, "[Timeline] -5t"));
        player.getInventory().setItem(2, named(Material.YELLOW_STAINED_GLASS_PANE, "[Timeline] -1t"));
        player.getInventory().setItem(4, named(Material.LIME_DYE, "[Timeline] Play/Pause"));
        player.getInventory().setItem(6, named(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "[Timeline] +1t"));
        player.getInventory().setItem(7, named(Material.BLUE_STAINED_GLASS_PANE, "[Timeline] +5t"));
        player.getInventory().setItem(8, named(Material.PURPLE_STAINED_GLASS_PANE, "[Timeline] +20t"));
    }

    private void restoreOriginalHotbar(Player player, EditorSession session) {
        for (int slot : EDITOR_SLOTS) {
            ItemStack current = player.getInventory().getItem(slot);
            if (!isEditorItem(current)) {
                continue;
            }

            ItemStack original = session.originalHotbar.get(slot);
            player.getInventory().setItem(slot, cloneIfPresent(original));
        }
    }

    private ItemStack cloneIfPresent(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class EditorSession {
        private final String sceneId;
        private int tick;
        private boolean playing;
        private final Map<Integer, ItemStack> originalHotbar = new HashMap<>();

        private EditorSession(String sceneId, int tick) {
            this.sceneId = sceneId;
            this.tick = tick;
        }
    }
}
