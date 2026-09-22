package me.psikuvit.copperHeist.world;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Empty worlds for arenas, so a map can live in its own world instead of somewhere far out in the overworld. The plugin creates them (no
 * other plugin needed) and remembers their names in {@code void-worlds.txt} in its own folder - not inside the world folder, whose location
 * differs between server versions - which is how it recognises them at the next start and loads them again with the same empty generator,
 * before the arenas that stand in them are read.
 */
public final class VoidWorlds {

    private static final String REGISTRY = "void-worlds.txt";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private VoidWorlds() {
    }

    /** World names become folder names, so only letters, digits, underscore and dash are allowed (no paths). */
    public static boolean validName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    // ---- the list of worlds this plugin made ----

    /** The names of the void worlds this plugin created, loaded or not. */
    public static List<String> names(Plugin plugin) {
        return new ArrayList<>(read(plugin));
    }

    public static boolean isVoidWorld(Plugin plugin, String name) {
        return validName(name) && read(plugin).contains(name);
    }

    private static Set<String> read(Plugin plugin) {
        Set<String> names = new LinkedHashSet<>();
        File file = new File(plugin.getDataFolder(), REGISTRY);
        if (!file.isFile()) return names;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (validName(line.trim())) names.add(line.trim());
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + REGISTRY, ex);
        }
        return names;
    }

    private static void register(Plugin plugin, String name) {
        Set<String> names = read(plugin);
        if (!names.add(name)) return;
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.write(new File(plugin.getDataFolder(), REGISTRY).toPath(), names, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + REGISTRY + " - '" + name + "' will not be loaded automatically at the next start", ex);
        }
    }

    // ---- creating and loading ----

    /** As {@link #createOrLoad(Plugin, String, boolean)} without adopting a world folder that already exists. */
    public static World createOrLoad(Plugin plugin, String name) {
        return createOrLoad(plugin, name, false);
    }

    /**
     * The world with this name: already loaded, loaded again (one of ours), or created new as an empty world with a small platform at spawn.
     * Returns null for an invalid name, or when a world of that name already exists on disk and is not one of ours - unless {@code adopt}
     * is true (an arena file that says {@code create-world: void} states that world is meant to be empty), in which case the existing world
     * is loaded and remembered as ours, without being changed.
     */
    public static World createOrLoad(Plugin plugin, String name, boolean adopt) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        if (!validName(name)) return null;

        boolean ours = isVoidWorld(plugin, name);
        boolean onDisk = existsOnDisk(name);
        if (onDisk && !ours && !adopt) return null; // somebody else's world: never load it with our generator

        World world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        if (world == null) return null;
        if (!onDisk) prepare(world);
        register(plugin, name);
        return world;
    }

    /**
     * Whether a world with this name already exists on disk. Servers keep extra worlds either next to the main world or, in newer versions,
     * inside the main world's folder, so both places are looked at.
     */
    static boolean existsOnDisk(String name) {
        File container = Bukkit.getWorldContainer();
        if (new File(container, name).exists()) return true;
        File[] folders = container.listFiles(File::isDirectory);
        if (folders == null) return false;
        for (File folder : folders) {
            if (new File(folder, "dimensions" + File.separator + "minecraft" + File.separator + name).exists()
                    || new File(folder, "dimensions" + File.separator + name).exists()) {
                return true;
            }
        }
        return false;
    }

    /** A 5x5 platform so the first player doesn't fall, a fixed spawn, and calm daytime weather. */
    private static void prepare(World world) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) world.getBlockAt(x, VoidGenerator.SPAWN_Y, z).setType(Material.SMOOTH_STONE);
        }
        world.setSpawnLocation(0, VoidGenerator.SPAWN_Y + 1, 0);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
    }
}
