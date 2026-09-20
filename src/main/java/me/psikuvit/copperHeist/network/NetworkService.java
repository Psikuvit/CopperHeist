package me.psikuvit.copperHeist.network;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Several game servers behind one proxy acting as one network. Everything shared goes through Redis
 * (network.redis.*), keyed by network.cluster so unrelated networks can share a Redis:
 * <ul>
 * <li>every server publishes the state of its arenas every few seconds, and reads everyone else's -
 *     that is how /ch list and /ch join see arenas on other servers;</li>
 * <li>joining a remote arena stores a short-lived "pending join" and connects the player through the proxy
 *     (BungeeCord plugin messaging, which Velocity also understands); the destination puts them in the arena when
 *     they arrive;</li>
 * <li>maintenance mode and admin broadcasts are network-wide.</li>
 * </ul>
 * Player stats are shared by pointing every server at the same MySQL database. With network.enabled: false this
 * class still exists (maintenance works locally) but never touches Redis.
 */
public class NetworkService {

    private static final String PROXY_CHANNEL = "BungeeCord";

    private final CopperHeist plugin;
    private volatile boolean redisUp = false;
    private volatile boolean localMaintenance = false;
    private volatile boolean networkMaintenance = false;
    private volatile List<RemoteArena> remote = List.of();

    private JedisPool pool;
    private BukkitTask statusTask;
    private Thread subscriber;
    private JedisPubSub pubSub;
    private volatile boolean running;
    private long lastErrorLogMillis = 0;

    public NetworkService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    // ---- settings ----

    private boolean enabled() {
        return plugin.settings().getBoolean("network.enabled", false);
    }

    public String serverId() {
        return plugin.settings().getString("network.server-id", "server-1");
    }

    private String proxyName() {
        String name = plugin.settings().getString("network.proxy-name", "");
        return name.isBlank() ? serverId() : name;
    }

    private String key(String suffix) {
        return "copperheist:" + plugin.settings().getString("network.cluster", "default") + ":" + suffix;
    }

    public boolean isConnected() {
        return redisUp;
    }

    // ---- lifecycle ----

    public void start() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, PROXY_CHANNEL);
        if (!enabled()) return;
        try {
            pool = new JedisPool(new JedisPoolConfig(),
                    plugin.settings().getString("network.redis.host", "localhost"),
                    plugin.settings().getInt("network.redis.port", 6379), 3000,
                    emptyToNull(plugin.settings().getString("network.redis.password", "")),
                    plugin.settings().getInt("network.redis.database", 0),
                    plugin.settings().getBoolean("network.redis.ssl", false));
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to Redis - multi-server features are off. "
                    + "Check the network.redis section of config.yml.", ex);
            closePool();
            return;
        }
        redisUp = true;
        running = true;

        long ticks = Math.max(1, plugin.settings().getLong("network.status-seconds", 3)) * 20L;
        statusTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncStatus, 20L, ticks);
        startSubscriber();
        plugin.getLogger().info("Multi-server enabled as '" + serverId() + "' in cluster '"
                + plugin.settings().getString("network.cluster", "default") + "'.");
    }

    public void shutdown() {
        running = false;
        if (statusTask != null) statusTask.cancel();
        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (RuntimeException ignored) {
                // already closed
            }
        }
        if (redisUp) {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(key("arenas:" + serverId()));
            } catch (RuntimeException ignored) {
                // Redis is gone; the entry expires on its own
            }
        }
        redisUp = false;
        closePool();
    }

    private void closePool() {
        if (pool != null) pool.close();
        pool = null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // ---- maintenance ----

    public boolean isMaintenance() {
        return localMaintenance || networkMaintenance;
    }

    /** Turns maintenance on or off; with Redis it applies to every server of the cluster. */
    public void setMaintenance(boolean on) {
        localMaintenance = on;
        if (!redisUp) return;
        async(jedis -> {
            if (on) jedis.set(key("maintenance"), "1");
            else jedis.del(key("maintenance"));
            networkMaintenance = on;
        });
    }

    // ---- remote arenas ----

    public List<RemoteArena> remoteArenas() {
        return remote;
    }

    /** Finds an arena on another server: by "server.arena", by arena name, or (name null) any joinable one. */
    public RemoteArena findRemote(String name) {
        RemoteArena best = null;
        for (RemoteArena candidate : remote) {
            if (!candidate.joinable()) continue;
            if (name != null && !candidate.arena().equalsIgnoreCase(name) && !candidate.fullName().equalsIgnoreCase(name)) continue;
            if (best == null || candidate.players() > best.players()) best = candidate;
        }
        return best;
    }

    /** Stores the pending join, then connects the player through the proxy. */
    public CompletableFuture<Boolean> transfer(Player player, RemoteArena target) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key("join:" + uuid), 30, target.serverId() + "|" + target.arena());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    connect(player, target.proxyName());
                    result.complete(true);
                });
            } catch (RuntimeException ex) {
                logError("Could not start a cross-server join", ex);
                result.complete(false);
            }
        });
        return result;
    }

    private void connect(Player player, String proxyServerName) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(proxyServerName);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not build the proxy connect message", ex);
            return;
        }
        player.sendPluginMessage(plugin, PROXY_CHANNEL, bytes.toByteArray());
    }

    /** Called when a player joins this server: if a cross-server join is waiting for them, put them in that arena. */
    public void onJoin(Player player) {
        if (!redisUp) return;
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String pending;
            try (Jedis jedis = pool.getResource()) {
                pending = jedis.get(key("join:" + uuid));
                if (pending != null) jedis.del(key("join:" + uuid));
            } catch (RuntimeException ex) {
                logError("Could not check for a pending cross-server join", ex);
                return;
            }
            if (pending == null) return;
            String[] parts = pending.split("\\|", 2);
            if (parts.length < 2 || !parts[0].equals(serverId())) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                var error = plugin.getGameManager().join(player, parts[1]);
                if (error != null) player.sendMessage(plugin.getMessageService().err(player, error));
            });
        });
    }

    // ---- broadcast ----

    /** Sends a MiniMessage line to every player on this server and, with Redis, on every other server. */
    public void broadcast(String miniMessage) {
        Bukkit.broadcast(Theme.mini().deserialize(miniMessage));
        if (redisUp) async(jedis -> jedis.publish(key("broadcast"), serverId() + " " + miniMessage));
    }

    // ---- background work ----

    /** Main thread: take a snapshot of the local arenas; then off-thread: write it, and read everyone else's. */
    private void syncStatus() {
        Map<String, String> mine = new HashMap<>();
        int max = plugin.settings().getInt("match.max-players", 16);
        for (Arena arena : plugin.getArenaManager().all()) {
            Game game = plugin.getGameManager().peek(arena);
            GameState state = game == null ? GameState.WAITING : game.getState();
            int players = game == null ? 0 : game.totalPlayers();
            mine.put(arena.getName(), state.name() + "|" + players + "|" + max + "|" + arena.isEnabled() + "|" + proxyName());
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (pool == null) return;
            try (Jedis jedis = pool.getResource()) {
                String own = key("arenas:" + serverId());
                jedis.del(own);
                if (!mine.isEmpty()) {
                    jedis.hset(own, mine);
                    jedis.expire(own, Math.max(10, plugin.settings().getInt("network.status-seconds", 3) * 4));
                }
                networkMaintenance = jedis.exists(key("maintenance"));
                remote = readRemote(jedis);
            } catch (RuntimeException ex) {
                logError("Lost the Redis connection", ex);
            }
        });
    }

    private List<RemoteArena> readRemote(Jedis jedis) {
        List<RemoteArena> found = new ArrayList<>();
        String prefix = key("arenas:");
        String cursor = ScanParams.SCAN_POINTER_START;
        ScanParams params = new ScanParams().match(prefix + "*").count(100);
        do {
            ScanResult<String> page = jedis.scan(cursor, params);
            for (String redisKey : page.getResult()) {
                String server = redisKey.substring(prefix.length());
                if (server.equals(serverId())) continue;
                for (Map.Entry<String, String> entry : jedis.hgetAll(redisKey).entrySet()) {
                    String[] parts = entry.getValue().split("\\|");
                    if (parts.length < 5) continue;
                    try {
                        found.add(new RemoteArena(server, parts[4], entry.getKey(), parts[0].toUpperCase(Locale.ROOT),
                                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Boolean.parseBoolean(parts[3])));
                    } catch (NumberFormatException ignored) {
                        // a malformed entry from some other version - skip it
                    }
                }
            }
            cursor = page.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
        return List.copyOf(found);
    }

    private void startSubscriber() {
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                int split = message.indexOf(' ');
                if (split < 0 || message.substring(0, split).equals(serverId())) return;
                String text = message.substring(split + 1);
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(Theme.mini().deserialize(text)));
            }
        };
        subscriber = new Thread(() -> {
            while (running) {
                try (Jedis jedis = pool.getResource()) {
                    jedis.subscribe(pubSub, key("broadcast"));
                } catch (RuntimeException ex) {
                    if (!running) return;
                    logError("Redis subscription dropped, retrying", ex);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }, "CopperHeist-Redis-Subscriber");
        subscriber.setDaemon(true);
        subscriber.start();
    }

    private void async(RedisWork work) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (pool == null) return;
            try (Jedis jedis = pool.getResource()) {
                work.run(jedis);
            } catch (RuntimeException ex) {
                logError("Redis command failed", ex);
            }
        });
    }

    private interface RedisWork {
        void run(Jedis jedis);
    }

    /** Errors repeat every few seconds while Redis is down - log at most once a minute. */
    private void logError(String message, RuntimeException ex) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLogMillis < 60_000) return;
        lastErrorLogMillis = now;
        plugin.getLogger().log(Level.WARNING, message, ex);
    }
}
