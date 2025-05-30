package com.Lino.autoLevelsUP;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AutoLevelsUP extends JavaPlugin implements Listener, TabCompleter {

    // Configuration cache
    private int levelCount;
    private long baseTime;
    private long timeIncrement;
    private int saveInterval;
    private boolean debugMode;
    private String prefix;
    private boolean actionBarEnabled;
    private boolean titleEnabled;
    private boolean soundEnabled;
    private String levelUpSound;

    // Database configuration
    private boolean databaseEnabled;
    private String databaseType;
    private String databaseHost;
    private int databasePort;
    private String databaseName;
    private String databaseUsername;
    private String databasePassword;
    private String databaseTable;
    private Connection connection;

    // Data files (used when database is disabled)
    private File dataFile;
    private YamlConfiguration dataConfig;

    // Thread-safe runtime data maps
    private final Map<UUID, Long> playtimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rankIndexes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();

    // Tasks
    private BukkitTask saveTask;
    private BukkitTask updateTask;
    private BukkitTask actionBarTask;

    // Performance cache
    private final Map<Integer, List<String>> commandsCache = new HashMap<>();
    private final Map<Integer, Long> requiredTimeCache = new HashMap<>();

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Load configuration
        if (!loadConfiguration()) {
            getLogger().severe("Error loading configuration. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Setup data storage (database or file)
        if (databaseEnabled) {
            if (!setupDatabase()) {
                getLogger().severe("Error setting up database. Plugin disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            if (!setupDataFile()) {
                getLogger().severe("Error setting up data file. Plugin disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        // Load existing data
        loadData();

        // Register events and commands
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("autolevelsup")).setTabCompleter(this);

        // Start periodic tasks
        startTasks();

        // Initialize sessions for online players
        initializeOnlinePlayers();

        getLogger().info(prefix + "Plugin enabled successfully!");
        getLogger().info(prefix + "Configured levels: " + levelCount);
        getLogger().info(prefix + "Data storage: " + (databaseEnabled ? "Database (" + databaseType + ")" : "File"));
    }

    @Override
    public void onDisable() {
        // Stop all tasks
        stopTasks();

        // Update playtime for online players
        updateOnlinePlaytimes();

        // Save final data
        saveData();

        // Close database connection if enabled
        if (databaseEnabled && connection != null) {
            try {
                connection.close();
                getLogger().info(prefix + "Database connection closed.");
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Error closing database connection:", e);
            }
        }

        getLogger().info(prefix + "Plugin disabled correctly.");
    }

    private boolean loadConfiguration() {
        try {
            reloadConfig();
            FileConfiguration config = getConfig();

            ConfigurationSection dynSection = config.getConfigurationSection("dynamicLevels");
            if (dynSection == null) {
                getLogger().severe("'dynamicLevels' section missing in configuration!");
                return false;
            }

            levelCount = dynSection.getInt("count", 10);
            baseTime = dynSection.getLong("baseTime", 3600);
            timeIncrement = dynSection.getLong("timeIncrement", 1800);
            saveInterval = config.getInt("settings.saveInterval", 300);
            debugMode = config.getBoolean("settings.debug", false);
            prefix = config.getString("settings.prefix", "§8[§6AutoLevelsUP§8] §r");

            // Database configuration
            databaseEnabled = config.getBoolean("database.enabled", false);
            if (databaseEnabled) {
                databaseType = config.getString("database.type", "sqlite").toLowerCase();
                databaseHost = config.getString("database.mysql.host", "localhost");
                databasePort = config.getInt("database.mysql.port", 3306);
                databaseName = config.getString("database.mysql.database", "minecraft");
                databaseUsername = config.getString("database.mysql.username", "user");
                databasePassword = config.getString("database.mysql.password", "password");
                databaseTable = config.getString("database.table", "autolevelsup_data");
            }

            // UI settings
            actionBarEnabled = config.getBoolean("ui.actionbar.enabled", true);
            titleEnabled = config.getBoolean("ui.title.enabled", true);
            soundEnabled = config.getBoolean("ui.sound.enabled", true);
            levelUpSound = config.getString("ui.sound.levelUpSound", "ENTITY_PLAYER_LEVELUP");

            // Pre-load commands and required times cache
            buildCaches();

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error loading configuration:", e);
            return false;
        }
    }

    private void buildCaches() {
        commandsCache.clear();
        requiredTimeCache.clear();

        ConfigurationSection levelsSection = getConfig().getConfigurationSection("dynamicLevels.levels");
        List<String> defaultCommands = getConfig().getStringList("dynamicLevels.commands");

        for (int i = 1; i <= levelCount; i++) {
            // Cache commands
            List<String> commands = defaultCommands;
            if (levelsSection != null && levelsSection.isConfigurationSection(String.valueOf(i))) {
                ConfigurationSection levelSection = levelsSection.getConfigurationSection(String.valueOf(i));
                if (levelSection != null && levelSection.contains("commands")) {
                    commands = levelSection.getStringList("commands");
                }
            }
            commandsCache.put(i, new ArrayList<>(commands));

            // Cache required time
            long requiredTime = baseTime + (timeIncrement * (i - 1));
            requiredTimeCache.put(i, requiredTime);
        }

        if (debugMode) {
            getLogger().info(prefix + "Caches built for " + levelCount + " levels");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("autolevelsup")) return false;

        if (args.length == 0) {
            sendUsageMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "remind":
                return handleRemind(sender);
            case "check":
                return handleCheck(sender, args);
            case "set":
                return handleSet(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "info":
                return handleInfo(sender);
            case "debug":
                return handleDebug(sender);
            default:
                sendUsageMessage(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("autolevelsup")) return null;

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String[] subCommands = {"reload", "remind", "check", "set", "reset", "info", "debug"};
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("check") ||
                args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("reset"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Initialize session time
        sessionStartTimes.put(uuid, System.currentTimeMillis());

        // Initialize data if new player
        playtimes.putIfAbsent(uuid, 0L);
        rankIndexes.putIfAbsent(uuid, 0);

        if (debugMode) {
            getLogger().info(prefix + "Player " + player.getName() + " joined. Session initialized.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Update playtime
        updatePlayerPlaytime(uuid);

        // Remove from session
        sessionStartTimes.remove(uuid);

        if (debugMode) {
            getLogger().info(prefix + "Player " + player.getName() + " left. Time updated.");
        }
    }

    private void startTasks() {
        // More frequent update task (every minute)
        updateTask = getServer().getScheduler().runTaskTimer(this, () -> {
            updateOnlinePlaytimes();
            checkAllPlayersForRankup();
        }, 20L * 60, 20L * 60); // Every minute

        // Less frequent save task
        saveTask = getServer().getScheduler().runTaskTimer(this,
                this::saveData,
                20L * saveInterval,
                20L * saveInterval);

        // ActionBar task (every 2 seconds)
        if (actionBarEnabled) {
            actionBarTask = getServer().getScheduler().runTaskTimer(this,
                    this::updateActionBars,
                    40L, 40L); // Every 2 seconds
        }
    }

    private void stopTasks() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        if (saveTask != null && !saveTask.isCancelled()) {
            saveTask.cancel();
        }
        if (actionBarTask != null && !actionBarTask.isCancelled()) {
            actionBarTask.cancel();
        }
    }

    private void updateActionBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendActionBar(player);
        }
    }

    private void sendActionBar(Player player) {
        UUID uuid = player.getUniqueId();

        // Update current time for precise calculation
        updatePlayerPlaytime(uuid);

        int currentRank = rankIndexes.getOrDefault(uuid, 0);
        long currentTime = playtimes.getOrDefault(uuid, 0L);

        String actionBarMessage;

        if (currentRank >= levelCount) {
            // Max level reached
            actionBarMessage = getConfig().getString("ui.actionbar.maxLevel",
                    "§a§l✓ MAX LEVEL REACHED! §7| §eTotal time: %total_time%");
            actionBarMessage = actionBarMessage.replace("%total_time%", formatTime(currentTime));
        } else {
            // Show progress towards next level
            int nextRank = currentRank + 1;
            Long requiredTime = requiredTimeCache.get(nextRank);

            if (requiredTime != null) {
                // Calculate progress based on current level requirements
                long currentLevelTime = 0L;
                if (currentRank > 0) {
                    Long prevRequiredTime = requiredTimeCache.get(currentRank);
                    if (prevRequiredTime != null) {
                        currentLevelTime = prevRequiredTime;
                    }
                }

                // Progress within current level range
                long progressTime = currentTime - currentLevelTime;
                long levelTimeRange = requiredTime - currentLevelTime;
                double progress = Math.min(1.0, Math.max(0.0, (double) progressTime / levelTimeRange));

                String progressBar = createProgressBar(progress, 20);
                long remaining = Math.max(0, requiredTime - currentTime);

                actionBarMessage = getConfig().getString("ui.actionbar.progress",
                        "§7Level §e%current_rank% §7→ §e%next_rank% §7| %progress_bar% §7| §e%remaining%");

                actionBarMessage = actionBarMessage
                        .replace("%current_rank%", String.valueOf(currentRank))
                        .replace("%next_rank%", String.valueOf(nextRank))
                        .replace("%progress_bar%", progressBar)
                        .replace("%remaining%", formatTime(remaining))
                        .replace("%progress_percent%", String.format("%.1f%%", progress * 100));
            } else {
                actionBarMessage = "§cError calculating progress";
            }
        }

        // Send ActionBar
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionBarMessage));
    }

    private String createProgressBar(double progress, int length) {
        StringBuilder bar = new StringBuilder();
        int filled = (int) (progress * length);

        String filledChar = getConfig().getString("ui.actionbar.progressBar.filledChar", "█");
        String emptyChar = getConfig().getString("ui.actionbar.progressBar.emptyChar", "░");
        String filledColor = getConfig().getString("ui.actionbar.progressBar.filledColor", "§a");
        String emptyColor = getConfig().getString("ui.actionbar.progressBar.emptyColor", "§7");

        bar.append(filledColor);
        for (int i = 0; i < filled; i++) {
            bar.append(filledChar);
        }

        bar.append(emptyColor);
        for (int i = filled; i < length; i++) {
            bar.append(emptyChar);
        }

        return bar.toString();
    }

    private void updateOnlinePlaytimes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerPlaytime(player.getUniqueId());
        }
    }

    private void updatePlayerPlaytime(UUID uuid) {
        Long sessionStart = sessionStartTimes.get(uuid);
        if (sessionStart != null) {
            long sessionTime = (System.currentTimeMillis() - sessionStart) / 1000; // Seconds
            playtimes.merge(uuid, sessionTime, Long::sum);
            sessionStartTimes.put(uuid, System.currentTimeMillis()); // Reset session start
        }
    }

    private void checkAllPlayersForRankup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAutoRank(player);
        }
    }

    private void checkAutoRank(Player player) {
        UUID uuid = player.getUniqueId();
        int currentRank = rankIndexes.getOrDefault(uuid, 0);

        if (currentRank >= levelCount) return;

        long currentPlaytime = playtimes.getOrDefault(uuid, 0L);
        int nextRank = currentRank + 1;
        Long requiredTime = requiredTimeCache.get(nextRank);

        if (requiredTime != null && currentPlaytime >= requiredTime) {
            performRankup(player, nextRank);
        }
    }

    private void performRankup(Player player, int newRank) {
        UUID uuid = player.getUniqueId();

        // Execute commands
        List<String> commands = commandsCache.get(newRank);
        if (commands != null) {
            executeCommands(player, newRank, commands);
        }

        // Visual and sound effects
        if (titleEnabled) {
            sendLevelUpTitle(player, newRank);
        }

        if (soundEnabled) {
            playLevelUpSound(player);
        }

        // Send message
        sendRankupMessage(player, newRank);

        // Update rank
        rankIndexes.put(uuid, newRank);

        // Debug log
        if (debugMode) {
            getLogger().info(prefix + "Player " + player.getName() + " promoted to level " + newRank);
        }

        // Check if can advance again
        getServer().getScheduler().runTaskLater(this, () -> checkAutoRank(player), 20L);
    }

    private void sendLevelUpTitle(Player player, int rank) {
        String title = getConfig().getString("ui.title.main", "§6§lLEVEL %rank%!");
        String subtitle = getConfig().getString("ui.title.sub", "§eCongratulations %player%!");

        title = title.replace("%rank%", String.valueOf(rank)).replace("%player%", player.getName());
        subtitle = subtitle.replace("%rank%", String.valueOf(rank)).replace("%player%", player.getName());

        int fadeIn = getConfig().getInt("ui.title.fadeIn", 10);
        int stay = getConfig().getInt("ui.title.stay", 70);
        int fadeOut = getConfig().getInt("ui.title.fadeOut", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    private void playLevelUpSound(Player player) {
        try {
            Sound sound = Sound.valueOf(levelUpSound);
            float volume = (float) getConfig().getDouble("ui.sound.volume", 1.0);
            float pitch = (float) getConfig().getDouble("ui.sound.pitch", 1.0);

            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound: " + levelUpSound + ". Using default sound.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("autolevelsup.reload")) {
            sender.sendMessage(prefix + "§cYou don't have permission to execute this command.");
            return true;
        }

        try {
            // Stop existing tasks
            stopTasks();

            // Save current data
            updateOnlinePlaytimes();
            saveData();

            // Reload configuration
            if (!loadConfiguration()) {
                sender.sendMessage(prefix + "§cError reloading configuration!");
                return true;
            }

            // Restart tasks
            startTasks();

            sender.sendMessage(prefix + "§aPlugin reloaded successfully!");
            getLogger().info(prefix + "Plugin reloaded by " + sender.getName());

        } catch (Exception e) {
            sender.sendMessage(prefix + "§cError during reload!");
            getLogger().log(Level.SEVERE, "Error during reload:", e);
        }

        return true;
    }

    private boolean handleRemind(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + "§cThis command can only be used by players.");
            return true;
        }

        sendReminder((Player) sender);
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("autolevelsup.check")) {
            sender.sendMessage(prefix + "§cYou don't have permission to execute this command.");
            return true;
        }

        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(prefix + "§cPlayer not found.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(prefix + "§cSpecify a player.");
            return true;
        }

        sendPlayerInfo(sender, target);
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("autolevelsup.set")) {
            sender.sendMessage(prefix + "§cYou don't have permission to execute this command.");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(prefix + "§eUsage: /autolevelsup set <player> <rank|time> <value>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(prefix + "§cPlayer not found.");
            return true;
        }

        String type = args[2].toLowerCase();
        try {
            int value = Integer.parseInt(args[3]);
            UUID uuid = target.getUniqueId();

            if (type.equals("rank")) {
                if (value < 0 || value > levelCount) {
                    sender.sendMessage(prefix + "§cInvalid rank (0-" + levelCount + ").");
                    return true;
                }
                rankIndexes.put(uuid, value);
                sender.sendMessage(prefix + "§a" + target.getName() + "'s rank set to " + value);
            } else if (type.equals("time")) {
                if (value < 0) {
                    sender.sendMessage(prefix + "§cInvalid time value.");
                    return true;
                }
                playtimes.put(uuid, (long) value);
                sender.sendMessage(prefix + "§a" + target.getName() + "'s time set to " + value + " seconds");
            } else {
                sender.sendMessage(prefix + "§eUsage: /autolevelsup set <player> <rank|time> <value>");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(prefix + "§cInvalid numeric value.");
        }

        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("autolevelsup.reset")) {
            sender.sendMessage(prefix + "§cYou don't have permission to execute this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(prefix + "§eUsage: /autolevelsup reset <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(prefix + "§cPlayer not found.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        playtimes.put(uuid, 0L);
        rankIndexes.put(uuid, 0);
        sessionStartTimes.put(uuid, System.currentTimeMillis());

        sender.sendMessage(prefix + "§a" + target.getName() + "'s data has been reset.");
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage(prefix + "§6=== AutoLevelsUP Info ===");
        sender.sendMessage("§7Version: §e2.0.0");
        sender.sendMessage("§7Configured levels: §e" + levelCount);
        sender.sendMessage("§7Online players: §e" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("§7Tracked players: §e" + playtimes.size());
        sender.sendMessage("§7Save interval: §e" + saveInterval + "s");
        sender.sendMessage("§7Debug mode: §e" + (debugMode ? "Active" : "Inactive"));
        sender.sendMessage("§7Data storage: §e" + (databaseEnabled ? "Database (" + databaseType + ")" : "File"));
        sender.sendMessage("§7ActionBar: §e" + (actionBarEnabled ? "Active" : "Inactive"));
        sender.sendMessage("§7Title: §e" + (titleEnabled ? "Active" : "Inactive"));
        sender.sendMessage("§7Sounds: §e" + (soundEnabled ? "Active" : "Inactive"));
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("autolevelsup.debug")) {
            sender.sendMessage(prefix + "§cYou don't have permission to execute this command.");
            return true;
        }

        debugMode = !debugMode;
        sender.sendMessage(prefix + "§7Debug mode: §e" + (debugMode ? "Activated" : "Deactivated"));
        return true;
    }

    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage(prefix + "§6=== AutoLevelsUP Commands ===");
        sender.sendMessage("§e/autolevelsup remind §7- Show remaining time");
        if (sender.hasPermission("autolevelsup.check")) {
            sender.sendMessage("§e/autolevelsup check [player] §7- Check player info");
        }
        if (sender.hasPermission("autolevelsup.set")) {
            sender.sendMessage("§e/autolevelsup set <player> <rank|time> <value> §7- Set value");
        }
        if (sender.hasPermission("autolevelsup.reset")) {
            sender.sendMessage("§e/autolevelsup reset <player> §7- Reset player");
        }
        if (sender.hasPermission("autolevelsup.reload")) {
            sender.sendMessage("§e/autolevelsup reload §7- Reload plugin");
        }
        sender.sendMessage("§e/autolevelsup info §7- Plugin information");
    }

    private void sendPlayerInfo(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();

        // Update time if online
        if (target.isOnline()) {
            updatePlayerPlaytime(uuid);
        }

        int currentRank = rankIndexes.getOrDefault(uuid, 0);
        long currentTime = playtimes.getOrDefault(uuid, 0L);

        sender.sendMessage(prefix + "§6=== " + target.getName() + " Info ===");
        sender.sendMessage("§7Current level: §e" + currentRank + "/" + levelCount);
        sender.sendMessage("§7Total time: §e" + formatTime(currentTime));

        if (currentRank < levelCount) {
            Long requiredTime = requiredTimeCache.get(currentRank + 1);
            if (requiredTime != null) {
                long remaining = requiredTime - currentTime;
                double progress = (double) currentTime / requiredTime * 100;
                sender.sendMessage("§7Time for next level: §e" + formatTime(Math.max(0, remaining)));
                sender.sendMessage("§7Progress: §e" + String.format("%.1f%%", progress));
                sender.sendMessage("§7Progress bar: " + createProgressBar(progress / 100, 20));
            }
        } else {
            sender.sendMessage("§a✓ Maximum level reached!");
        }
    }

    private void executeCommands(Player player, int rank, List<String> commands) {
        for (String cmd : commands) {
            String processedCmd = cmd
                    .replace("%player%", player.getName())
                    .replace("%rank%", String.valueOf(rank))
                    .replace("%uuid%", player.getUniqueId().toString());

            getServer().getScheduler().runTask(this, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd));
        }
    }

    private void sendRankupMessage(Player player, int rank) {
        String rankupMsg = getConfig().getString("message.rankup",
                "§a§l✓ Congratulations %player%! You reached level %rank%!");

        player.sendMessage(rankupMsg
                .replace("%player%", player.getName())
                .replace("%rank%", String.valueOf(rank)));
    }

    private void sendReminder(Player player) {
        UUID uuid = player.getUniqueId();

        // Update current time
        updatePlayerPlaytime(uuid);

        int currentRank = rankIndexes.getOrDefault(uuid, 0);

        if (currentRank >= levelCount) {
            player.sendMessage(prefix + "§a✓ You have already reached the maximum level!");
            return;
        }

        Long requiredTime = requiredTimeCache.get(currentRank + 1);
        if (requiredTime == null) return;

        long currentTime = playtimes.getOrDefault(uuid, 0L);
        long remaining = requiredTime - currentTime;
        double progress = (double) currentTime / requiredTime * 100;

        String template = getConfig().getString("message.template",
                "§7Current level: §e%current_rank%§7, " +
                        "Next level in: §e%time_remaining% §7(§e%progress%§7)");

        player.sendMessage(template
                .replace("%player%", player.getName())
                .replace("%current_rank%", String.valueOf(currentRank))
                .replace("%next_rank%", String.valueOf(currentRank + 1))
                .replace("%time_remaining%", formatTime(Math.max(0, remaining)))
                .replace("%progress%", String.format("%.1f%%", progress)));
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    private void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            sessionStartTimes.put(uuid, System.currentTimeMillis());
            playtimes.putIfAbsent(uuid, 0L);
            rankIndexes.putIfAbsent(uuid, 0);
        }
    }

    private boolean setupDatabase() {
        try {
            if (databaseType.equals("mysql")) {
                // MySQL connection
                String url = "jdbc:mysql://" + databaseHost + ":" + databasePort + "/" + databaseName + "?useSSL=false&autoReconnect=true";
                connection = DriverManager.getConnection(url, databaseUsername, databasePassword);
                getLogger().info(prefix + "Connected to MySQL database.");
            } else {
                // SQLite connection
                File dbFile = new File(getDataFolder(), "data.db");
                if (!getDataFolder().exists()) {
                    getDataFolder().mkdirs();
                }
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                getLogger().info(prefix + "Connected to SQLite database.");
            }

            // Create table if not exists
            createTable();
            return true;

        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Error setting up database:", e);
            return false;
        }
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + databaseTable + " (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "playtime BIGINT NOT NULL DEFAULT 0, " +
                "rank_index INT NOT NULL DEFAULT 0" +
                ")";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
            if (debugMode) {
                getLogger().info(prefix + "Database table '" + databaseTable + "' ready.");
            }
        }
    }

    private boolean setupDataFile() {
        if (databaseEnabled) return true; // Skip file setup if database is enabled

        try {
            dataFile = new File(getDataFolder(), "data.yml");

            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            return true;

        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error setting up data file:", e);
            return false;
        }
    }

    private void loadData() {
        if (databaseEnabled) {
            loadDataFromDatabase();
        } else {
            loadDataFromFile();
        }
    }

    private void loadDataFromDatabase() {
        try {
            String sql = "SELECT uuid, playtime, rank_index FROM " + databaseTable;
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        long playtime = rs.getLong("playtime");
                        int rankIndex = rs.getInt("rank_index");

                        playtimes.put(uuid, playtime);
                        rankIndexes.put(uuid, rankIndex);
                        count++;

                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid UUID in database: " + rs.getString("uuid"));
                    }
                }

                getLogger().info(prefix + "Loaded data for " + count + " players from database.");
            }

        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Error loading data from database:", e);
        }
    }

    private void loadDataFromFile() {
        try {
            if (dataConfig.isConfigurationSection("players")) {
                ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");

                for (String key : playersSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        long time = dataConfig.getLong("players." + key + ".time", 0L);
                        int rank = dataConfig.getInt("players." + key + ".rankIndex", 0);

                        playtimes.put(uuid, time);
                        rankIndexes.put(uuid, rank);

                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid UUID in data file: " + key);
                    }
                }
            }

            getLogger().info(prefix + "Loaded data for " + playtimes.size() + " players from file.");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error loading data from file:", e);
        }
    }

    private void saveData() {
        if (databaseEnabled) {
            saveDataToDatabase();
        } else {
            saveDataToFile();
        }
    }

    private void saveDataToDatabase() {
        try {
            // Use REPLACE or INSERT ... ON DUPLICATE KEY UPDATE for MySQL
            // Use INSERT OR REPLACE for SQLite
            String sql;
            if (databaseType.equals("mysql")) {
                sql = "INSERT INTO " + databaseTable + " (uuid, playtime, rank_index) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE playtime = VALUES(playtime), rank_index = VALUES(rank_index)";
            } else {
                sql = "INSERT OR REPLACE INTO " + databaseTable + " (uuid, playtime, rank_index) VALUES (?, ?, ?)";
            }

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                int batchCount = 0;

                for (Map.Entry<UUID, Long> entry : playtimes.entrySet()) {
                    UUID uuid = entry.getKey();
                    long playtime = entry.getValue();
                    int rankIndex = rankIndexes.getOrDefault(uuid, 0);

                    stmt.setString(1, uuid.toString());
                    stmt.setLong(2, playtime);
                    stmt.setInt(3, rankIndex);
                    stmt.addBatch();

                    batchCount++;
                    if (batchCount % 50 == 0) { // Execute batch every 50 records
                        stmt.executeBatch();
                        stmt.clearBatch();
                    }
                }

                // Execute remaining records
                if (batchCount % 50 != 0) {
                    stmt.executeBatch();
                }

                if (debugMode) {
                    getLogger().info(prefix + "Data saved for " + playtimes.size() + " players to database.");
                }
            }

        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Error saving data to database:", e);
        }
    }

    private void saveDataToFile() {
        try {
            // Clear existing section
            dataConfig.set("players", null);

            // Save current data
            for (Map.Entry<UUID, Long> entry : playtimes.entrySet()) {
                UUID uuid = entry.getKey();
                String path = "players." + uuid.toString();

                dataConfig.set(path + ".time", entry.getValue());
                dataConfig.set(path + ".rankIndex", rankIndexes.getOrDefault(uuid, 0));
            }

            // Save to file
            dataConfig.save(dataFile);

            if (debugMode) {
                getLogger().info(prefix + "Data saved for " + playtimes.size() + " players to file.");
            }

        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error saving data to file:", e);
        }
    }
}