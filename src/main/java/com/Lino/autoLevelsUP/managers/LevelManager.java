package com.Lino.autoLevelsUP.managers;

import com.Lino.autoLevelsUP.AutoLevelsUP;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class LevelManager {
    private final AutoLevelsUP plugin;
    private final ConfigManager config;
    private final PlayerManager playerManager;

    private final Map<Integer, List<String>> commandsCache = new HashMap<>();
    private final Map<Integer, Long> requiredTimeCache = new HashMap<>();

    public LevelManager(AutoLevelsUP plugin, ConfigManager config, PlayerManager playerManager) {
        this.plugin = plugin;
        this.config = config;
        this.playerManager = playerManager;
        buildCaches();
    }

    public void buildCaches() {
        commandsCache.clear();
        requiredTimeCache.clear();
        List<String> defaultCmds = config.getStringList("dynamicLevels.commands");

        for (int i = 1; i <= config.getLevelCount(); i++) {
            List<String> cmds = defaultCmds;
            // Check specific level commands
            if (config.getConfig().contains("dynamicLevels.levels." + i + ".commands")) {
                cmds = config.getStringList("dynamicLevels.levels." + i + ".commands");
            }
            commandsCache.put(i, new ArrayList<>(cmds));
            long reqTime = config.getBaseTime() + (config.getTimeIncrement() * (i - 1));
            requiredTimeCache.put(i, reqTime);
        }
    }

    public Long getRequiredTime(int rank) {
        return requiredTimeCache.get(rank);
    }

    public void checkRankup(Player player) {
        UUID uuid = player.getUniqueId();
        int currentRank = playerManager.getRank(uuid);

        if (currentRank >= config.getLevelCount()) return;

        long time = playerManager.getPlaytime(uuid);
        int nextRank = currentRank + 1;
        Long reqTime = requiredTimeCache.get(nextRank);

        if (reqTime != null && time >= reqTime) {
            performRankup(player, nextRank);
        }
    }

    private void performRankup(Player player, int newRank) {
        // 1. Commands
        List<String> cmds = commandsCache.get(newRank);
        if (cmds != null) executeCommands(player, newRank, cmds);

        // 2. Milestones
        if (config.getConfig().contains("rewards.milestones." + newRank)) {
            executeCommands(player, newRank, config.getStringList("rewards.milestones." + newRank));
        }

        // 3. Random Rewards
        if (config.getConfig().getBoolean("rewards.enabled")) {
            if (Math.random() <= config.getConfig().getDouble("rewards.chance", 0.0)) {
                List<String> rewards = config.getStringList("rewards.randomRewards");
                if (!rewards.isEmpty()) {
                    String reward = rewards.get(new Random().nextInt(rewards.size()));
                    executeCommands(player, newRank, Collections.singletonList(reward));
                    player.sendMessage(config.getPrefix() + "§eYou received a random reward!");
                }
            }
        }

        // 4. Effects
        if (config.isTitleEnabled()) {
            player.sendTitle("§6§lLEVEL " + newRank + "!", "§eCongratulations " + player.getName() + "!", 10, 70, 20);
        }
        if (config.isSoundEnabled()) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(config.getLevelUpSound()), 1f, 1f);
            } catch (Exception e) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }

        // 5. Update
        playerManager.setRank(player.getUniqueId(), newRank);
        player.sendMessage(config.getConfig().getString("message.rankup", "Congratulations!").replace("%rank%", String.valueOf(newRank)));

        // Re-check logic
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkRankup(player), 20L);
    }

    private void executeCommands(Player player, int rank, List<String> commands) {
        for (String cmd : commands) {
            String finalCmd = cmd.replace("%player%", player.getName()).replace("%rank%", String.valueOf(rank));
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
        }
    }
}