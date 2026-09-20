package me.psikuvit.copperHeist.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Brings an older config.yml up to the current layout. A backup is written
 * before anything changes; each version step only renames or moves keys - new
 * keys need no step because the bundled config supplies their defaults.
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 1;

    private ConfigMigrator() {
    }

    public static void migrate(JavaPlugin plugin) {
        migrateVersion(plugin);
        addNewOptions(plugin);
    }

    /** Writes options added by an update into config.yml (existing values and comments stay), on every start. */
    private static void addNewOptions(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration defaults = ConfigFiles.bundled(plugin, "config.yml");
        if (defaults == null || !file.exists()) return;
        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            return; // the plugin reports an unreadable config elsewhere; don't overwrite it
        }
        if (ConfigFiles.addMissing(plugin, file, user, defaults) > 0) plugin.reloadConfig();
    }

    private static void migrateVersion(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int version = config.getInt("config-version", 0);
        if (version >= CURRENT_VERSION) return;

        File file = new File(plugin.getDataFolder(), "config.yml");
        if (file.exists()) {
            File backup = new File(plugin.getDataFolder(), "config.yml.bak-v" + version);
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backed up config.yml to " + backup.getName() + " before migrating.");
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not back up config.yml", ex);
            }
        }

        // v0 -> v1: baseline, nothing moved. Future steps go here, e.g. if (version < 2) { ... }
        config.set("config-version", CURRENT_VERSION);
        plugin.saveConfig();
        plugin.getLogger().info("config.yml migrated to version " + CURRENT_VERSION + ".");
    }
}
