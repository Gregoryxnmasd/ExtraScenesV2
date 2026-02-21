package com.extracraft.extrascenesv2.listeners;

import com.extracraft.extrascenesv2.commands.ExtraScenesCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ActorRecordingListener implements Listener {

    private final ExtraScenesCommand command;

    public ActorRecordingListener(ExtraScenesCommand command) {
        this.command = command;
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (!command.isActorSaveItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        command.saveActorRecording(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        command.stopActorRecording(event.getPlayer().getUniqueId());
    }
}
