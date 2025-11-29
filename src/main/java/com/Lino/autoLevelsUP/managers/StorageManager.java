package com.Lino.autoLevelsUP.managers;

import com.Lino.autoLevelsUP.AutoLevelsUP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class StorageManager {
    private final AutoLevelsUP plugin;
    private final ConfigManager config;

    private Connection connection;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private boolean isDb;

    public StorageManager(AutoLevelsUP plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean setup() {
        this.isDb = config.getConfig().getBoolean("database.enabled", false);

        if (isDb) {
            return setupDatabase();
        } else {
            return setupFile();
        }
    }

    private boolean setupDatabase() {
        String type = config.getConfig().getString("database.type", "sqlite");
        String table = config.getConfig().getString("database.table", "autolevelsup_data");

        try {
            if (type.equalsIgnoreCase("mysql")) {
                String host = config.getConfig().getString("database.mysql.host");
                int port = config.getConfig().getInt("database.mysql.port");
                String db = config.getConfig().getString("database.mysql.database");
                String user = config.getConfig().getString("database.mysql.username");
                String pass = config.getConfig().getString("database.mysql.password");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&autoReconnect=true";
                connection = DriverManager.getConnection(url, user, pass);
            } else {
                File dbFile = new File(plugin.getDataFolder(), "data.db");
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + table + " (uuid VARCHAR(36) PRIMARY KEY, playtime BIGINT DEFAULT 0, rank_index INT DEFAULT 0)")) {
                stmt.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error:", e);
            return false;
        }
    }

    private boolean setupFile() {
        try {
            dataFile = new File(plugin.getDataFolder(), "data.yml");
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!dataFile.exists()) dataFile.createNewFile();
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not setup data.yml");
            return false;
        }
    }

    public void loadData(Map<UUID, Long> playtimes, Map<UUID, Integer> ranks) {
        if (isDb) {
            String table = config.getConfig().getString("database.table", "autolevelsup_data");
            try (PreparedStatement stmt = connection.prepareStatement("SELECT uuid, playtime, rank_index FROM " + table);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    playtimes.put(uuid, rs.getLong("playtime"));
                    ranks.put(uuid, rs.getInt("rank_index"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading DB data: " + e.getMessage());
            }
        } else {
            if (dataConfig.isConfigurationSection("players")) {
                for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                    UUID uuid = UUID.fromString(key);
                    playtimes.put(uuid, dataConfig.getLong("players." + key + ".time"));
                    ranks.put(uuid, dataConfig.getInt("players." + key + ".rankIndex"));
                }
            }
        }
    }

    public void saveData(Map<UUID, Long> playtimes, Map<UUID, Integer> ranks) {
        if (isDb) {
            String table = config.getConfig().getString("database.table", "autolevelsup_data");
            String sql = config.getConfig().getString("database.type", "sqlite").equalsIgnoreCase("mysql") ?
                    "INSERT INTO " + table + " (uuid, playtime, rank_index) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE playtime=VALUES(playtime), rank_index=VALUES(rank_index)" :
                    "INSERT OR REPLACE INTO " + table + " (uuid, playtime, rank_index) VALUES (?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (UUID uuid : playtimes.keySet()) {
                    stmt.setString(1, uuid.toString());
                    stmt.setLong(2, playtimes.get(uuid));
                    stmt.setInt(3, ranks.getOrDefault(uuid, 0));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving DB data: " + e.getMessage());
            }
        } else {
            dataConfig.set("players", null);
            for (UUID uuid : playtimes.keySet()) {
                dataConfig.set("players." + uuid + ".time", playtimes.get(uuid));
                dataConfig.set("players." + uuid + ".rankIndex", ranks.getOrDefault(uuid, 0));
            }
            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Error saving file data: " + e.getMessage());
            }
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}