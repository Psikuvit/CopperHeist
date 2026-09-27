package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * The navigators placed with /ch navigator, kept in navigators.yml. Every change is written straight back to the file. A navigator in a world
 * that isn't loaded is skipped with a warning.
 */
public class NavigatorRegistry {

    /** One placed navigator; {@code look} is a navigator-looks.yml look id, or null for the default navigator look. */
    public record Navigator(String id, Location location, String look) {
    }

    private final CopperHeist plugin;
    private final File file;
    private final Map<String, Navigator> navigators = new LinkedHashMap<>();

    public NavigatorRegistry(CopperHeist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "navigators.yml");
    }

    public void load() {
        navigators.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("navigators");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;
            Location loc = LocationUtil.deserialize(entry.getString("world"), entry.getList("loc"));
            if (loc == null) {
                plugin.getLogger().warning("Navigator '" + id + "' is in a world that isn't loaded - skipped.");
                continue;
            }
            navigators.put(id.toLowerCase(Locale.ROOT), new Navigator(id.toLowerCase(Locale.ROOT), loc, entry.getString("look")));
        }
    }

    public Collection<Navigator> all() {
        return new ArrayList<>(navigators.values());
    }

    public Navigator get(String id) {
        return id == null ? null : navigators.get(id.toLowerCase(Locale.ROOT));
    }

    /** Adds or replaces a navigator and saves the file. */
    public Navigator put(String id, Location location, String look) {
        String key = id.toLowerCase(Locale.ROOT);
        Navigator navigator = new Navigator(key, location, look == null ? null : look.toLowerCase(Locale.ROOT));
        navigators.put(key, navigator);
        save();
        return navigator;
    }

    /** Removes a navigator and saves the file. Returns the removed navigator, or null if there was none by that id. */
    public Navigator remove(String id) {
        Navigator removed = navigators.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) save();
        return removed;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Navigator navigator : navigators.values()) {
            String base = "navigators." + navigator.id();
            yaml.set(base + ".world", navigator.location().getWorld().getName());
            yaml.set(base + ".loc", LocationUtil.serialize(navigator.location()));
            if (navigator.look() != null) yaml.set(base + ".look", navigator.look());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save navigators.yml", ex);
        }
    }
}
