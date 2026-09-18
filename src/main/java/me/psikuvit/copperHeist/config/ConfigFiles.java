package me.psikuvit.copperHeist.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Loads a bundled YAML resource from the plugin folder: copies it out on first
 * run, layers the jar's version underneath as defaults so keys added by an
 * update never come back missing, and turns a syntax error into a log line
 * plus the built-in defaults instead of a stack trace and a broken plugin.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);

        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, name + " could not be read (" + ex.getMessage()
                    + ") - using the built-in defaults until it is fixed.");
            user = new YamlConfiguration();
        }

        YamlConfiguration defaults = bundled(plugin, name);
        if (defaults != null) user.setDefaults(defaults);
        return user;
    }

    public static YamlConfiguration bundled(JavaPlugin plugin, String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + name, ex);
            return null;
        }
    }
}
