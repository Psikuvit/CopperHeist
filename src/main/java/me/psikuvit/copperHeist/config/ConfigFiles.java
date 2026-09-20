package me.psikuvit.copperHeist.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /** Files whose keys are plain settings, so keys added by an update are written into the server's copy. */
    private static final Set<String> WRITE_MISSING = Set.of("scoreboard.yml", "guide.yml");

    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);

        YamlConfiguration user = new YamlConfiguration();
        boolean readable = true;
        try {
            user.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, name + " could not be read (" + ex.getMessage()
                    + ") - using the built-in defaults until it is fixed.");
            user = new YamlConfiguration();
            readable = false;
        }

        YamlConfiguration defaults = bundled(plugin, name);
        if (defaults != null && readable && WRITE_MISSING.contains(name)) addMissing(plugin, file, user, defaults);
        if (defaults != null) user.setDefaults(defaults);
        return user;
    }

    /**
     * Copies every key the jar's {@code defaults} have and the server's file lacks into that file (with its
     * comments), so new options show up in the file instead of only working invisibly through defaults. Existing
     * values are never touched, and the old file is kept as {@code <name>.bak} first. Only call it on files whose
     * keys are settings - not on ones where a missing key means the owner deleted it on purpose.
     *
     * @return how many keys were added
     */
    public static int addMissing(JavaPlugin plugin, File file, YamlConfiguration user, YamlConfiguration defaults) {
        List<String> missing = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !user.contains(key, true)) missing.add(key);
        }
        if (missing.isEmpty()) return 0;

        try {
            Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not back up " + file.getName() + " - leaving it unchanged.", ex);
            return 0;
        }

        for (String key : missing) {
            // A section the file lacks entirely also takes the comment that heads it in the jar's copy.
            String[] parts = key.split("\\.");
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) path.append('.');
                path.append(parts[i]);
                String section = path.toString();
                if (!user.contains(section, true)) {
                    user.createSection(section);
                    user.setComments(section, defaults.getComments(section));
                }
            }
            user.set(key, defaults.get(key));
            user.setComments(key, defaults.getComments(key));
            user.setInlineComments(key, defaults.getInlineComments(key));
        }
        try {
            user.save(file);
            plugin.getLogger().info("Added " + missing.size() + " new option(s) to " + file.getName() + ": "
                    + String.join(", ", missing.size() > 8 ? missing.subList(0, 8) : missing)
                    + (missing.size() > 8 ? ", ..." : "") + " (old file kept as " + file.getName() + ".bak)");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + file.getName(), ex);
        }
        return missing.size();
    }

    /** Loads any YAML file (no copying), layering {@code defaults} underneath; a syntax error yields defaults only. */
    public static YamlConfiguration loadFile(JavaPlugin plugin, File file, YamlConfiguration defaults) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, file.getName() + " could not be read (" + ex.getMessage() + ") - using defaults.");
            yaml = new YamlConfiguration();
        }
        if (defaults != null) yaml.setDefaults(defaults);
        return yaml;
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
