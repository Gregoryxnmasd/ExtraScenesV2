package com.extracraft.extrascenesv2.listeners;

import com.extracraft.extrascenesv2.editor.TimelineEditorService;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class TimelineEditorListener implements Listener {

    private final TimelineEditorService timelineEditorService;

    public TimelineEditorListener(TimelineEditorService timelineEditorService) {
        this.timelineEditorService = timelineEditorService;
    }

    @EventHandler
    public void onEditorHotbarClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
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
    public void onEditorInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!timelineEditorService.hasSession(player)) {
            return;
        }
        if (!timelineEditorService.isEditorSlot(event.getSlot())) {
            return;
        }

        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    @EventHandler
    public void onEditorItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!timelineEditorService.hasSession(player)) {
            return;
        }
        if (!timelineEditorService.isEditorItem(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        timelineEditorService.handleQuit(event.getPlayer());
    }
}
