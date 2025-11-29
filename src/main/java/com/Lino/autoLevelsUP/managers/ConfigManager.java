package com.Lino.autoLevelsUP.managers;

import com.Lino.autoLevelsUP.AutoLevelsUP;
import com.Lino.autoLevelsUP.utils.ColorUtils; // Importa utils
import org.bukkit.configuration.ConfigurationSection;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {
    private final AutoLevelsUP plugin;

    // Settings
    private int levelCount;
    private long baseTime;
    private long timeIncrement;
    private int saveInterval;
    private boolean debugMode;
    private String prefix;

    // UI
    private boolean titleEnabled;
    private boolean soundEnabled;
    private String levelUpSound;
    private float soundVolume; // NEW
    private float soundPitch;  // NEW

    // Anti-AFK
    private boolean antiAfkEnabled;
    private int antiAfkTimeout;

    public ConfigManager(AutoLevelsUP plugin) {
        this.plugin = plugin;
    }

    public boolean load() {
        try {
            plugin.reloadConfig();
            ConfigurationSection dynSection = plugin.getConfig().getConfigurationSection("dynamicLevels");
            if (dynSection == null) {
                plugin.getLogger().severe("'dynamicLevels' section missing!");
                return false;
            }

            this.levelCount = dynSection.getInt("count", 10);
            this.baseTime = dynSection.getLong("baseTime", 3600);
            this.timeIncrement = dynSection.getLong("timeIncrement", 1800);

            this.saveInterval = plugin.getConfig().getInt("settings.saveInterval", 300);
            this.debugMode = plugin.getConfig().getBoolean("settings.debug", false);
            // Applica colori al prefisso
            this.prefix = ColorUtils.process(plugin.getConfig().getString("settings.prefix", "&8[&6AutoLevelsUP&8] &r"));

            this.antiAfkEnabled = plugin.getConfig().getBoolean("settings.antiAfk.enabled", true);
            this.antiAfkTimeout = plugin.getConfig().getInt("settings.antiAfk.timeout", 300);

            this.titleEnabled = plugin.getConfig().getBoolean("ui.title.enabled", true);
            this.soundEnabled = plugin.getConfig().getBoolean("ui.sound.enabled", true);
            this.levelUpSound = plugin.getConfig().getString("ui.sound.levelUpSound", "ENTITY_PLAYER_LEVELUP");
            // Carica volume e pitch
            this.soundVolume = (float) plugin.getConfig().getDouble("ui.sound.volume", 1.0);
            this.soundPitch = (float) plugin.getConfig().getDouble("ui.sound.pitch", 1.0);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading config:", e);
            return false;
        }
    }

    // Getters
    public int getLevelCount() { return levelCount; }
    public long getBaseTime() { return baseTime; }
    public long getTimeIncrement() { return timeIncrement; }
    public int getSaveInterval() { return saveInterval; }
    public boolean isDebugMode() { return debugMode; }
    public String getPrefix() { return prefix; }
    public boolean isAntiAfkEnabled() { return antiAfkEnabled; }
    public int getAntiAfkTimeout() { return antiAfkTimeout; }
    public boolean isTitleEnabled() { return titleEnabled; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public String getLevelUpSound() { return levelUpSound; }
    public float getSoundVolume() { return soundVolume; } // NEW
    public float getSoundPitch() { return soundPitch; }   // NEW

    public ConfigurationSection getConfig() { return plugin.getConfig(); }
    public List<String> getStringList(String path) { return plugin.getConfig().getStringList(path); }
}