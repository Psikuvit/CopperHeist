package me.psikuvit.copperHeist.ui;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Where a shop keeper look is shown to a player who previews it (/ch setpreview): the spot the keeper stands on and the way it faces.
 * The camera sits in front of it. Saved in preview.yml.
 */
public class PreviewStage {

    private static final double EYE_HEIGHT = 1.7;
    private static final double TARGET_HEIGHT = 1.0;

    private final CopperHeist plugin;
    private final File file;
    private Location location;

    public PreviewStage(CopperHeist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "preview.yml");
    }

    public void load() {
        location = null;
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        World world = Bukkit.getWorld(yaml.getString("world", ""));
        if (world == null) {
            plugin.getLogger().warning("preview.yml names a world that isn't loaded - the preview stage is ignored.");
            return;
        }
        location = new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"), (float) yaml.getDouble("yaw"), 0f);
    }

    /** The keeper's spot (facing included), or null if no stage is set. */
    public Location get() {
        return location == null ? null : location.clone();
    }

    /** Where the camera sits: {@code distance} blocks in front of the keeper, at eye height, looking at its chest. */
    public Location camera(double distance) {
        if (location == null) return null;
        double radians = Math.toRadians(location.getYaw());
        Location camera = location.clone().add(-Math.sin(radians) * distance, EYE_HEIGHT, Math.cos(radians) * distance);
        camera.setYaw(location.getYaw() + 180f);
        camera.setPitch((float) Math.toDegrees(Math.atan2(EYE_HEIGHT - TARGET_HEIGHT, distance)));
        return camera;
    }

    public void set(Location where) {
        Location spot = new Location(where.getWorld(), where.getBlockX() + 0.5, where.getY(), where.getBlockZ() + 0.5, where.getYaw(), 0f);
        location = spot;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", spot.getWorld().getName());
        yaml.set("x", spot.getX());
        yaml.set("y", spot.getY());
        yaml.set("z", spot.getZ());
        yaml.set("yaw", (double) spot.getYaw());
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save preview.yml", ex);
        }
    }
}
