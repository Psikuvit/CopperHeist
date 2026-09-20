package me.psikuvit.copperHeist.stats;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.LocationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Keeps the top players for every stat in memory (refreshed from the database on a timer, so nothing asks the
 * database when a player types /ch top) and drives the floating text boards placed with /ch leaderboard create.
 * Boards are TextDisplays that aren't saved with the world; they are respawned whenever their chunk is loaded.
 */
public class LeaderboardService {

    /** One placed board. */
    public static final class Board {
        private final String id;
        private final Stat stat;
        private final Location location;
        private TextDisplay display;

        private Board(String id, Stat stat, Location location) {
            this.id = id;
            this.stat = stat;
            this.location = location;
        }

        public String id() {
            return id;
        }

        public Stat stat() {
            return stat;
        }

        public Location location() {
            return location;
        }
    }

    private final CopperHeist plugin;
    private final StatsRepository repository;
    private final File file;
    private final Map<Stat, List<TopEntry>> cache = new ConcurrentHashMap<>();
    private final Map<String, Board> boards = new LinkedHashMap<>();
    private BukkitTask refreshTask;
    private BukkitTask keepAliveTask;

    public LeaderboardService(CopperHeist plugin, StatsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
    }

    public void start() {
        loadBoards();
        long ticks = Math.max(10, plugin.settings().getLong("leaderboards.refresh-seconds", 120)) * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, ticks);
        keepAliveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnMissing, 60L, 200L);
    }

    public void stop() {
        if (refreshTask != null) refreshTask.cancel();
        if (keepAliveTask != null) keepAliveTask.cancel();
        for (Board board : boards.values()) removeDisplay(board);
    }

    public int lines() {
        return Math.max(1, plugin.settings().getInt("leaderboards.lines", 10));
    }

    /** The cached top list for a stat (empty until the first refresh finishes). */
    public List<TopEntry> top(Stat stat) {
        return cache.getOrDefault(stat, List.of());
    }

    /** Re-reads every stat's top list, then redraws the boards. Safe to call from the main thread. */
    public void refreshAll() {
        Map<Stat, List<TopEntry>> fresh = new EnumMap<>(Stat.class);
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            pending.add(repository.top(stat, lines()).thenAccept(rows -> {
                synchronized (fresh) {
                    fresh.put(stat, rows);
                }
            }));
        }
        CompletableFuture.allOf(pending.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not refresh the leaderboards", error);
                        return;
                    }
                    cache.putAll(fresh);
                    Bukkit.getScheduler().runTask(plugin, this::redrawAll);
                });
    }

    // ---- boards ----

    public Collection<Board> boards() {
        return boards.values();
    }

    public Board create(String id, Stat stat, Location location) {
        String key = id.toLowerCase(Locale.ROOT);
        Board old = boards.remove(key);
        if (old != null) removeDisplay(old);
        Board board = new Board(key, stat, location.clone());
        boards.put(key, board);
        save();
        spawnMissing();
        return board;
    }

    public boolean remove(String id) {
        Board board = boards.remove(id.toLowerCase(Locale.ROOT));
        if (board == null) return false;
        removeDisplay(board);
        save();
        return true;
    }

    private void spawnMissing() {
        for (Board board : boards.values()) {
            World world = board.location.getWorld();
            if (world == null || !world.isChunkLoaded(board.location.getBlockX() >> 4, board.location.getBlockZ() >> 4)) continue;
            if (board.display != null && board.display.isValid()) continue;
            board.display = world.spawn(board.location, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setViewRange(1.5f);
                entity.text(render(board));
            });
        }
    }

    private void redrawAll() {
        for (Board board : boards.values()) {
            if (board.display != null && board.display.isValid()) board.display.text(render(board));
        }
    }

    private void removeDisplay(Board board) {
        if (board.display != null) board.display.remove();
        board.display = null;
    }

    /** The lang key for a leaderboard row: the top three get their own style. */
    public static String lineKey(int rank) {
        return rank >= 1 && rank <= 3 ? "leaderboard.line-top" + rank : "leaderboard.line";
    }

    private Component render(Board board) {
        var messages = plugin.getMessageService();
        Component text = messages.get("leaderboard.title", "stat", messages.rawFor(Bukkit.getConsoleSender(), board.stat.langKey()));
        List<TopEntry> rows = top(board.stat);
        if (rows.isEmpty()) return text.appendNewline().append(messages.get("leaderboard.empty"));
        for (TopEntry row : rows) {
            text = text.appendNewline().append(messages.get(lineKey(row.rank()),
                    "rank", row.rank(), "player", row.name(), "value", row.value()));
        }
        return text;
    }

    // ---- persistence ----

    private void loadBoards() {
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

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Board board : boards.values()) {
            String base = "boards." + board.id;
            yaml.set(base + ".stat", board.stat.key());
            yaml.set(base + ".world", board.location.getWorld().getName());
            yaml.set(base + ".x", board.location.getX());
            yaml.set(base + ".y", board.location.getY());
            yaml.set(base + ".z", board.location.getZ());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save leaderboards.yml", ex);
        }
    }
}
