package me.psikuvit.copperHeist.ui;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** The server's hub spawn (/ch setlobby): where players land when they join and where /ch lobby takes them. Saved in lobby.yml. */
public class HubSpawn {

    private final CopperHeist plugin;
    private final File file;
    private Location location;

    public HubSpawn(CopperHeist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lobby.yml");
    }

    public void load() {
        location = null;
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        location = LocationUtil.deserializeCentered(yaml.getString("world"), yaml.getList("spawn"));
        if (location == null) plugin.getLogger().warning("lobby.yml has a spawn in a world that isn't loaded - the hub spawn is ignored.");
    }

    /** The hub spawn, or null if none is set (or its world isn't loaded). */
    public Location get() {
        return location == null ? null : location.clone();
    }

    public void set(Location where) {
        location = where.clone();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", where.getWorld().getName());
        yaml.set("spawn", LocationUtil.serialize(where));
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save lobby.yml", ex);
        }
    }
}
