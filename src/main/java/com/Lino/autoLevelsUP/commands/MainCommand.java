package com.Lino.autoLevelsUP.commands;

import com.Lino.autoLevelsUP.AutoLevelsUP;
import com.Lino.autoLevelsUP.managers.*;
import com.Lino.autoLevelsUP.utils.ColorUtils; // Importa utils
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final AutoLevelsUP plugin;
    private final ConfigManager config;
    private final PlayerManager playerManager;
    private final LevelManager levelManager;

    public MainCommand(AutoLevelsUP plugin, ConfigManager config, PlayerManager playerManager, LevelManager levelManager) {
        this.plugin = plugin;
        this.config = config;
        this.playerManager = playerManager;
        this.levelManager = levelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!checkPerm(sender, "autolevelsup.reload")) return true;
                config.load();
                levelManager.buildCaches();
                sender.sendMessage(config.getPrefix() + ColorUtils.process("&aReloaded!"));
                break;
            case "check":
                if (!checkPerm(sender, "autolevelsup.check")) return true;
                handleCheck(sender, args);
                break;
            case "set":
                if (!checkPerm(sender, "autolevelsup.set")) return true;
                handleSet(sender, args);
                break;
            case "reset":
                if (!checkPerm(sender, "autolevelsup.reset")) return true;
                handleReset(sender, args);
                break;
            default:
                sendHelp(sender);
        }
        return true;
    }

    private void handleCheck(CommandSender sender, String[] args) {
        Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
        if (target == null) {
            sender.sendMessage(config.getPrefix() + ColorUtils.process("&cPlayer not found."));
            return;
        }

        UUID uuid = target.getUniqueId();
        int rank = playerManager.getRank(uuid);
        long time = playerManager.getPlaytime(uuid);

        sender.sendMessage(config.getPrefix() + ColorUtils.process("&6=== &e" + target.getName() + " &6==="));
        sender.sendMessage(ColorUtils.process("&7Level: &e" + rank + "/" + config.getLevelCount()));

        Long req = levelManager.getRequiredTime(rank + 1);
        if (req != null) {
            long left = Math.max(0, req - time);
            double pct = Math.min(100.0, (double) time / req * 100);
            sender.sendMessage(ColorUtils.process("&7Next in: &e" + formatTime(left)));
            sender.sendMessage(ColorUtils.process("&7Progress: &e" + String.format("%.1f%%", pct)));
        } else {
            sender.sendMessage(ColorUtils.process("&aMax Level Reached!"));
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ColorUtils.process("&eUsage: /alu set <player> <rank|time> <val>"));
            return;
        }
        Player t = Bukkit.getPlayer(args[1]);
        if (t == null) {
            sender.sendMessage(ColorUtils.process("&cOffline."));
            return;
        }
        try {
            int val = Integer.parseInt(args[3]);
            if (args[2].equalsIgnoreCase("rank")) {
                playerManager.setRank(t.getUniqueId(), Math.min(val, config.getLevelCount()));
                sender.sendMessage(ColorUtils.process("&aRank set to " + val));
            } else if (args[2].equalsIgnoreCase("time")) {
                playerManager.setPlaytime(t.getUniqueId(), val);
                sender.sendMessage(ColorUtils.process("&aTime set to " + val));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.process("&cInvalid number."));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtils.process("&eUsage: /alu reset <player>"));
            return;
        }
        Player t = Bukkit.getPlayer(args[1]);
        if (t == null) {
            sender.sendMessage(ColorUtils.process("&cOffline."));
            return;
        }
        playerManager.resetData(t.getUniqueId());
        sender.sendMessage(ColorUtils.process("&aReset complete."));
    }

    private boolean checkPerm(CommandSender s, String perm) {
        if (!s.hasPermission(perm)) {
            s.sendMessage(config.getPrefix() + ColorUtils.process("&cNo permission."));
            return false;
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(ColorUtils.process("&e/alu check, /alu reload, /alu set, /alu reset"));
    }

    private String formatTime(long s) {
        return String.format("%02dh %02dm %02ds", s / 3600, (s % 3600) / 60, s % 60);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "check", "set", "reset");
        return null;
    }
}