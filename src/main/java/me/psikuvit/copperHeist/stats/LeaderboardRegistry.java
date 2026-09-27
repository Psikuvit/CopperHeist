package me.psikuvit.copperHeist.stats;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * The floating leaderboards placed with /ch leaderboard create, kept in leaderboards.yml. Every change is written straight back to the
 * file. A board with an unknown stat or in a world that isn't loaded is skipped with a warning.
 */
public class LeaderboardRegistry {

    /** One placed board. */
    public record Board(String id, Stat stat, Location location) {
    }

    private final CopperHeist plugin;
    private final File file;
    private final Map<String, Board> boards = new LinkedHashMap<>();

    public LeaderboardRegistry(CopperHeist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
    }

    public void load() {
        boards.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("boards");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;
            Stat stat = Stat.fromKey(entry.getString("stat"));
            World world = Bukkit.getWorld(entry.getString("world", ""));
            if (stat == null || world == null) {
                plugin.getLogger().warning("Skipping leaderboard '" + id + "' (unknown stat or world)");
                continue;
            }
            boards.put(id, new Board(id, stat, LocationUtil.center(new Location(world, entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")))));
        }
    }

    public List<Board> all() {
        return new ArrayList<>(boards.values());
    }

    /** Adds a board (a board with the same id is replaced and moves to the end) and saves the file. */
    public Board put(String id, Stat stat, Location location) {
        String key = id.toLowerCase(Locale.ROOT);
        Board board = new Board(key, stat, location.clone());
        boards.remove(key);
        boards.put(key, board);
        save();
        return board;
    }

    /** Removes a board and saves the file. Returns the removed board, or null if there was none by that id. */
    public Board remove(String id) {
        Board removed = boards.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) save();
        return removed;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Board board : boards.values()) {
            String base = "boards." + board.id();
            yaml.set(base + ".stat", board.stat().key());
            yaml.set(base + ".world", board.location().getWorld().getName());
            yaml.set(base + ".x", board.location().getX());
            yaml.set(base + ".y", board.location().getY());
            yaml.set(base + ".z", board.location().getZ());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save leaderboards.yml", ex);
        }
    }
}
