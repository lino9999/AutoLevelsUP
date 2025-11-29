package com.Lino.autoLevelsUP.managers;

import com.Lino.autoLevelsUP.AutoLevelsUP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final AutoLevelsUP plugin;
    private final ConfigManager config;
    private final StorageManager storage;

    private final Map<UUID, Long> playtimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rankIndexes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActiveTimes = new ConcurrentHashMap<>();

    public PlayerManager(AutoLevelsUP plugin, ConfigManager config, StorageManager storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        // Load initial data
        storage.loadData(playtimes, rankIndexes);
    }

    public void loadOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            initSession(p.getUniqueId());
        }
    }

    public void initSession(UUID uuid) {
        sessionStartTimes.put(uuid, System.currentTimeMillis());
        lastActiveTimes.put(uuid, System.currentTimeMillis());
        playtimes.putIfAbsent(uuid, 0L);
        rankIndexes.putIfAbsent(uuid, 0);
    }

    public void closeSession(UUID uuid) {
        updateTime(uuid);
        sessionStartTimes.remove(uuid);
        lastActiveTimes.remove(uuid);
    }

    public void updateActivity(UUID uuid) {
        lastActiveTimes.put(uuid, System.currentTimeMillis());
    }

    public void updateOnlinePlayersTime() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTime(p.getUniqueId());
        }
    }

    private void updateTime(UUID uuid) {
        Long sessionStart = sessionStartTimes.get(uuid);
        if (sessionStart != null) {
            long now = System.currentTimeMillis();

            if (config.isAntiAfkEnabled()) {
                Long lastActive = lastActiveTimes.getOrDefault(uuid, now);
                if (now - lastActive > config.getAntiAfkTimeout() * 1000L) {
                    sessionStartTimes.put(uuid, now); // Reset session start, no points gained
                    return;
                }
            }

            long gained = (now - sessionStart) / 1000;
            if (gained > 0) {
                playtimes.merge(uuid, gained, Long::sum);
                sessionStartTimes.put(uuid, now);
            }
        }
    }

    public void saveAll() {
        updateOnlinePlayersTime();
        storage.saveData(playtimes, rankIndexes);
    }

    // Accessors
    public long getPlaytime(UUID uuid) { return playtimes.getOrDefault(uuid, 0L); }
    public void setPlaytime(UUID uuid, long time) { playtimes.put(uuid, time); }
    public int getRank(UUID uuid) { return rankIndexes.getOrDefault(uuid, 0); }
    public void setRank(UUID uuid, int rank) { rankIndexes.put(uuid, rank); }
    public void resetData(UUID uuid) {
        playtimes.put(uuid, 0L);
        rankIndexes.put(uuid, 0);
        sessionStartTimes.put(uuid, System.currentTimeMillis());
    }
}