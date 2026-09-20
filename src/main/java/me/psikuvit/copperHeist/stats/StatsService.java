package me.psikuvit.copperHeist.stats;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The in-memory front of the stats database. Gameplay code calls {@link #add} freely on the main thread;
 * deltas pile up per player and are written in batches - on a timer, when a player quits, when a match ends
 * and on shutdown - so the game never waits on the database. Online players' stored values are cached so
 * /ch stats and placeholders read instantly.
 */
public class StatsService {

    private final CopperHeist plugin;
    private final StatsRepository repository;
    private final Map<UUID, Map<Stat, Long>> stored = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Stat, Long>> pending = new HashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private BukkitTask flushTask;

    public StatsService(CopperHeist plugin, StatsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public StatsRepository repository() {
        return repository;
    }

    public void start() {
        long ticks = Math.max(5, plugin.settings().getLong("stats.flush-seconds", 30)) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushAll, ticks, ticks);
        // Players already online are loaded by ProfileService.start(), which waits for the network hand-off first.
    }

    /** Stops the timer and blocks (briefly) until everything pending has been written. */
    public void shutdown() {
        if (flushTask != null) flushTask.cancel();
        try {
            flushAll().get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Some stats could not be saved on shutdown", ex);
        }
        repository.close();
    }

    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        names.put(uuid, player.getName());
        repository.touchPlayer(uuid, player.getName())
                .thenCompose(ignored -> repository.load(uuid))
                .whenComplete((values, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not load stats for " + player.getName(), error);
                        return;
                    }
                    stored.put(uuid, new ConcurrentHashMap<>(values));
                });
    }

    /** Writes the player's pending stats and forgets them; the future completes once the database has them. */
    public CompletableFuture<Void> onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        return flush(uuid).whenComplete((ignored, error) -> {
            stored.remove(uuid);
            names.remove(uuid);
        });
    }

    /** Records progress for a player (safe from the main thread; nothing touches the database here). */
    public void add(UUID uuid, String name, Stat stat, long amount) {
        if (amount == 0) return;
        names.putIfAbsent(uuid, name);
        synchronized (pending) {
            pending.computeIfAbsent(uuid, id -> new EnumMap<>(Stat.class)).merge(stat, amount, Long::sum);
        }
    }

    /** A player's stats right now: what the database held when they joined plus everything not yet written. */
    public PlayerStats snapshot(UUID uuid, String name) {
        Map<Stat, Long> merged = new EnumMap<>(Stat.class);
        Map<Stat, Long> base = stored.get(uuid);
        if (base != null) merged.putAll(base);
        synchronized (pending) {
            Map<Stat, Long> waiting = pending.get(uuid);
            if (waiting != null) waiting.forEach((stat, amount) -> merged.merge(stat, amount, Long::sum));
        }
        return new PlayerStats(uuid, name, merged);
    }

    /** Admin: sets a stat to an exact value (pending progress is written first so nothing is lost or double counted). */
    public CompletableFuture<Void> set(UUID uuid, Stat stat, long value) {
        return flush(uuid).thenCompose(ignored -> repository.setStat(uuid, stat, value))
                .thenRun(() -> stored.computeIfPresent(uuid, (id, values) -> {
                    values.put(stat, value);
                    return values;
                }));
    }

    /** Admin: wipes a player's stats. */
    public CompletableFuture<Void> reset(UUID uuid) {
        synchronized (pending) {
            pending.remove(uuid);
        }
        return repository.resetPlayer(uuid).thenRun(() -> stored.computeIfPresent(uuid, (id, values) -> {
            values.clear();
            return values;
        }));
    }

    public boolean isLoaded(UUID uuid) {
        return stored.containsKey(uuid);
    }

    public CompletableFuture<Void> flush(UUID uuid) {
        Map<Stat, Long> deltas;
        synchronized (pending) {
            deltas = pending.remove(uuid);
        }
        if (deltas == null || deltas.isEmpty()) return CompletableFuture.completedFuture(null);
        stored.computeIfPresent(uuid, (id, values) -> {
            deltas.forEach((stat, amount) -> values.merge(stat, amount, Long::sum));
            return values;
        });
        return repository.addAll(uuid, deltas).whenComplete((ignored, error) -> {
            if (error == null) return;
            plugin.getLogger().log(Level.WARNING, "Could not save stats for " + names.getOrDefault(uuid, uuid.toString())
                    + " - keeping them to retry", error);
            stored.computeIfPresent(uuid, (id, values) -> {
                deltas.forEach((stat, amount) -> values.merge(stat, -amount, Long::sum));
                return values;
            });
            synchronized (pending) {
                Map<Stat, Long> again = pending.computeIfAbsent(uuid, id -> new EnumMap<>(Stat.class));
                deltas.forEach((stat, amount) -> again.merge(stat, amount, Long::sum));
            }
        });
    }

    public CompletableFuture<Void> flushAll() {
        List<UUID> ids;
        synchronized (pending) {
            ids = new ArrayList<>(pending.keySet());
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID id : ids) futures.add(flush(id));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
