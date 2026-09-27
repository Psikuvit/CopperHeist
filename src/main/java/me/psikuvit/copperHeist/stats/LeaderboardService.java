package me.psikuvit.copperHeist.stats;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.stats.LeaderboardRegistry.Board;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Keeps the top players for every stat in memory (refreshed from the database on a timer, so nothing asks the
 * database when a player types /ch top) and drives the floating text boards placed with /ch leaderboard create
 * ({@link LeaderboardRegistry}). Boards are TextDisplays that aren't saved with the world; they are respawned
 * whenever their chunk is loaded.
 */
public class LeaderboardService {

    private final CopperHeist plugin;
    private final StatsRepository repository;
    private final LeaderboardRegistry registry;
    private final Map<Stat, List<TopEntry>> cache = new ConcurrentHashMap<>();
    private final Map<String, TextDisplay> displays = new HashMap<>();
    private BukkitTask refreshTask;
    private BukkitTask keepAliveTask;

    public LeaderboardService(CopperHeist plugin, StatsRepository repository, LeaderboardRegistry registry) {
        this.plugin = plugin;
        this.repository = repository;
        this.registry = registry;
    }

    public LeaderboardRegistry registry() {
        return registry;
    }

    public void start() {
        registry.load();
        long ticks = Math.max(10, plugin.settings().getLong("leaderboards.refresh-seconds", 120)) * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, ticks);
        keepAliveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnMissing, 60L, 200L);
    }

    public void stop() {
        if (refreshTask != null) refreshTask.cancel();
        if (keepAliveTask != null) keepAliveTask.cancel();
        removeAllDisplays();
    }

    /** Re-reads leaderboards.yml and rebuilds every board (after /ch reload). */
    public void reload() {
        removeAllDisplays();
        registry.load();
        spawnMissing();
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

    public Board create(String id, Stat stat, Location location) {
        Board board = registry.put(id, stat, location);
        removeDisplay(board.id());
        spawnMissing();
        return board;
    }

    public boolean remove(String id) {
        Board removed = registry.remove(id);
        if (removed == null) return false;
        removeDisplay(removed.id());
        return true;
    }

    private void spawnMissing() {
        for (Board board : registry.all()) {
            Location at = board.location();
            World world = at.getWorld();
            if (world == null || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            TextDisplay display = displays.get(board.id());
            if (display != null && display.isValid()) continue;
            displays.put(board.id(), world.spawn(at, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setViewRange(1.5f);
                entity.text(render(board));
            }));
        }
    }

    private void redrawAll() {
        for (Board board : registry.all()) {
            TextDisplay display = displays.get(board.id());
            if (display != null && display.isValid()) display.text(render(board));
        }
    }

    private void removeDisplay(String id) {
        TextDisplay display = displays.remove(id);
        if (display != null) display.remove();
    }

    private void removeAllDisplays() {
        for (TextDisplay display : displays.values()) display.remove();
        displays.clear();
    }

    /** The lang key for a leaderboard row: the top three get their own style. */
    public static String lineKey(int rank) {
        return rank >= 1 && rank <= 3 ? "leaderboard.line-top" + rank : "leaderboard.line";
    }

    private Component render(Board board) {
        var messages = plugin.getMessageService();
        Component text = messages.get("leaderboard.title", "stat", messages.rawFor(Bukkit.getConsoleSender(), board.stat().langKey()));
        List<TopEntry> rows = top(board.stat());
        if (rows.isEmpty()) return text.appendNewline().append(messages.get("leaderboard.empty"));
        for (TopEntry row : rows) {
            text = text.appendNewline().append(messages.get(lineKey(row.rank()),
                    "rank", row.rank(), "player", row.name(), "value", plugin.getProgress() == null
                            ? String.valueOf(row.value()) : plugin.getProgress().display(board.stat(), row.value())));
        }
        return text;
    }
}
