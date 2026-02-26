package com.extracraft.extrascenesv2.listeners;

import com.extracraft.extrascenesv2.cinematics.CinematicPlaybackService;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public final class CinematicProtectionListener implements Listener {

    private final CinematicPlaybackService playbackService;

    public CinematicProtectionListener(CinematicPlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !playbackService.isInCinematic(player)) {
            return;
        }

        if (event.getSlotType() == InventoryType.SlotType.ARMOR || event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !playbackService.isInCinematic(player)) {
            return;
        }

        if (event.getRawSlots().stream().anyMatch(slot -> slot == 5)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (playbackService.isInCinematic(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHotbarSelect(PlayerItemHeldEvent event) {
        if (playbackService.isInCinematic(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPumpkinUse(PlayerInteractEvent event) {
        if (!playbackService.isInCinematic(event.getPlayer())) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.CARVED_PUMPKIN) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playbackService.handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        playbackService.handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playbackService.handleJoin(event.getPlayer());
    }
}
