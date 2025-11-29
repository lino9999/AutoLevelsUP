package com.Lino.autoLevelsUP;

import com.Lino.autoLevelsUP.commands.MainCommand;
import com.Lino.autoLevelsUP.hooks.PAPIHook;
import com.Lino.autoLevelsUP.listeners.GameListener;
import com.Lino.autoLevelsUP.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoLevelsUP extends JavaPlugin {

    private ConfigManager configManager;
    private StorageManager storageManager;
    private PlayerManager playerManager;
    private LevelManager levelManager;

    @Override
    public void onEnable() {
        // 1. Load Config
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        if (!configManager.load()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Setup Storage
        this.storageManager = new StorageManager(this, configManager);
        if (!storageManager.setup()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Setup Managers
        this.playerManager = new PlayerManager(this, configManager, storageManager);
        this.levelManager = new LevelManager(this, configManager, playerManager);

        // 4. Register Commands & Events
        getCommand("autolevelsup").setExecutor(new MainCommand(this, configManager, playerManager, levelManager));
        getServer().getPluginManager().registerEvents(new GameListener(this, playerManager), this);

        // 5. Register PAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIHook(this, playerManager, levelManager).register();
            getLogger().info("PlaceholderAPI hooked successfully!");
        }

        // 6. Start Tasks
        startTasks();
        playerManager.loadOnlinePlayers(); // Reload friendly

        getLogger().info(configManager.getPrefix() + "Plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Stop tasks
        getServer().getScheduler().cancelTasks(this);

        // Save data
        if (playerManager != null) {
            playerManager.saveAll();
        }

        // Close storage
        if (storageManager != null) {
            storageManager.close();
        }

        getLogger().info("Plugin disabled correctly.");
    }

    private void startTasks() {
        // Update Task (Playtime & Rankup check)
        getServer().getScheduler().runTaskTimer(this, () -> {
            playerManager.updateOnlinePlayersTime();
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                levelManager.checkRankup(p);
            }
        }, 20L * 30, 20L * 30); // Every 30s

        // Save Task
        getServer().getScheduler().runTaskTimer(this, () -> {
            playerManager.saveAll();
        }, 20L * configManager.getSaveInterval(), 20L * configManager.getSaveInterval());
    }
}