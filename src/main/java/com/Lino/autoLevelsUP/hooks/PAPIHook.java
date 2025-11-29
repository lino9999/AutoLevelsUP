package com.Lino.autoLevelsUP.hooks;

import com.Lino.autoLevelsUP.AutoLevelsUP;
import com.Lino.autoLevelsUP.managers.LevelManager;
import com.Lino.autoLevelsUP.managers.PlayerManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PAPIHook extends PlaceholderExpansion {
    private final AutoLevelsUP plugin;
    private final PlayerManager pm;
    private final LevelManager lm;

    public PAPIHook(AutoLevelsUP plugin, PlayerManager pm, LevelManager lm) {
        this.plugin = plugin;
        this.pm = pm;
        this.lm = lm;
    }

    @Override
    public @NotNull String getIdentifier() { return "autolevelsup"; }
    @Override
    public @NotNull String getAuthor() { return "Lino"; }
    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player p, @NotNull String params) {
        if (p == null) return "";

        // Refresh data before showing
        pm.updateOnlinePlayersTime();

        if (params.equals("level")) return String.valueOf(pm.getRank(p.getUniqueId()));
        if (params.equals("time")) return formatTime(pm.getPlaytime(p.getUniqueId()));

        if (params.equals("next_time")) {
            Long req = lm.getRequiredTime(pm.getRank(p.getUniqueId()) + 1);
            return req != null ? formatTime(Math.max(0, req - pm.getPlaytime(p.getUniqueId()))) : "Max";
        }

        return null;
    }

    private String formatTime(long s) {
        return String.format("%02dh %02dm %02ds", s / 3600, (s % 3600) / 60, s % 60);
    }
}