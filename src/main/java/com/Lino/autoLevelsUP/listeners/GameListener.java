package com.Lino.autoLevelsUP.listeners;

import com.Lino.autoLevelsUP.AutoLevelsUP;
import com.Lino.autoLevelsUP.managers.PlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {
    private final AutoLevelsUP plugin;
    private final PlayerManager playerManager;

    public GameListener(AutoLevelsUP plugin, PlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        playerManager.initSession(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerManager.closeSession(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
                e.getFrom().getBlockY() == e.getTo().getBlockY() &&
                e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        playerManager.updateActivity(e.getPlayer().getUniqueId());
    }
}