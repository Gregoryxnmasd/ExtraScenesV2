package com.extracraft.extrascenesv2.listeners;

import com.extracraft.extrascenesv2.editor.TimelineEditorService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TimelineEditorListener implements Listener {

    private final TimelineEditorService timelineEditorService;

    public TimelineEditorListener(TimelineEditorService timelineEditorService) {
        this.timelineEditorService = timelineEditorService;
    }

    @EventHandler
    public void onEditorHotbarClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!timelineEditorService.isEditorItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        timelineEditorService.handleHotbarAction(player, player.getInventory().getHeldItemSlot());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        timelineEditorService.handleQuit(event.getPlayer());
    }
}
