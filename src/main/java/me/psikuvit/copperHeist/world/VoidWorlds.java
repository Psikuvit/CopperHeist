package me.psikuvit.copperHeist.world;

import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Empty worlds for arenas, so a map can live in its own world instead of somewhere far out in the overworld. The plugin creates them (no
 * other plugin needed) and marks their folder with a small file, which is how it recognises them at the next start and loads them again
 * with the same empty generator - before the arenas that stand in them are read.
 */
public final class VoidWorlds {

    /** The marker file put in the world folder of a world this plugin created. */
    static final String MARKER = "copperheist.void";

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private VoidWorlds() {
    }

    /** World names become folder names, so only letters, digits, underscore and dash are allowed (no paths). */
    public static boolean validName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /** True if a world folder with this name exists and was created by this plugin. */
    public static boolean isVoidWorld(String name) {
        return validName(name) && new File(new File(Bukkit.getWorldContainer(), name), MARKER).isFile();
    }

    /** The names of the void worlds that exist, loaded or not. */
    public static List<String> names() {
        List<String> found = new ArrayList<>();
        File[] folders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (folders == null) return found;
        for (File folder : folders) {
            if (new File(folder, MARKER).isFile()) found.add(folder.getName());
        }
        return found;
    }

    /**
     * The world with this name: already loaded, loaded from its folder (a void world of ours), or created new as an empty world with a small
     * platform at spawn. Returns null for an invalid name, or when a world folder exists that is not one of ours (it is not touched).
     */
    public static World createOrLoad(Plugin plugin, String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        if (!validName(name)) return null;

        File folder = new File(Bukkit.getWorldContainer(), name);
        boolean fresh = !folder.exists();
        if (!fresh && !isVoidWorld(name)) return null; // somebody else's world: never load it with our generator

        World world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        if (world == null) return null;
        if (fresh) {
            mark(plugin, folder);
            prepare(world);
        }
        return world;
    }

    private static void mark(Plugin plugin, File folder) {
        try {
            if (!new File(folder, MARKER).createNewFile()) plugin.getLogger().fine("Void world marker already existed in " + folder);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not mark " + folder.getName() + " as a void world - it will not be reloaded automatically", ex);
        }
    }

    /** A 5x5 platform so the first player doesn't fall, a fixed spawn, and calm daytime weather. */
    private static void prepare(World world) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) world.getBlockAt(x, VoidGenerator.SPAWN_Y, z).setType(Material.SMOOTH_STONE);
        }
        world.setSpawnLocation(0, VoidGenerator.SPAWN_Y + 1, 0);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
    }
}
