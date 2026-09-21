package me.psikuvit.copperHeist.profile;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The player's saved data across the whole network: their stats (with XP and coins) and their {@link PlayerProfile}. This class owns the
 * join/quit order that keeps it consistent when several servers share one database:
 * <ol>
 * <li>on join it waits (via Redis) until whichever server had the player last has saved and released them, then claims them and loads
 *     their stats and profile;</li>
 * <li>while they play, changes stay in memory and are written on a timer and after important actions ({@link #save});</li>
 * <li>on quit it writes their stats and profile and only then releases them, so the next server never reads stale data.</li>
 * </ol>
 * Without Redis (single server) there is nothing to wait for and the same code just loads and saves. Main thread only, except where noted.
 */
public class ProfileService {

    private final CopperHeist plugin;
    private final ProfileRepository repository;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private BukkitTask saveTask;

    public ProfileService(CopperHeist plugin, ProfileRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** Starts the autosave timer and loads everyone already online (a plugin reload). */
    public void start() {
        long ticks = Math.max(5, plugin.settings().getLong("stats.flush-seconds", 30)) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveDirty, ticks, ticks);
        for (Player player : Bukkit.getOnlinePlayers()) onJoin(player);
    }

    /** Writes everything, waits (briefly) for the database, and releases every player so their next server need not wait. */
    public void shutdown() {
        if (saveTask != null) saveTask.cancel();
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (PlayerProfile profile : profiles.values()) {
            if (profile.isDirty()) writes.add(repository.save(profile.uuid(), profile.snapshot()));
        }
        StatsService stats = plugin.getStats();
        if (stats != null) writes.add(stats.flushAll());
        try {
            CompletableFuture.allOf(writes.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Some player data could not be saved on shutdown", ex);
        }
        profiles.clear();
        plugin.getNetwork().releaseAllProfiles();
    }

    // ---- lifecycle of one player ----

    /** Join: wait for the previous server to let go of the player, then load their stats and profile. */
    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getNetwork().claimProfile(uuid).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                plugin.getNetwork().releaseProfile(uuid); // left while waiting - nothing was loaded, just give the claim back
                return;
            }
            load(player);
        }));
    }

    private void load(Player player) {
        UUID uuid = player.getUniqueId();
        StatsService stats = plugin.getStats();
        if (stats != null) stats.onJoin(player);
        repository.load(uuid).whenComplete((data, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not load the profile of " + player.getName() + " - profile features are off for them", error);
                return;
            }
            if (!player.isOnline()) return;
            boolean isNew = data.fields().get(PlayerProfile.FIRST_JOIN) == null;
            PlayerProfile profile = new PlayerProfile(uuid, data, isNew);
            long now = System.currentTimeMillis();
            if (isNew) profile.setField(PlayerProfile.FIRST_JOIN, now);
            profile.setField(PlayerProfile.LAST_LOGIN, now);
            profiles.put(uuid, profile);
            save(profile);
            if (plugin.getCosmetics() != null) plugin.getCosmetics().onProfileLoaded(player);
            if (plugin.getDaily() != null) plugin.getDaily().onLogin(player);
        }));
    }

    /** Quit: write stats and profile, and only when both are in the database let another server take the player. */
    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProfile profile = profiles.remove(uuid);
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        StatsService stats = plugin.getStats();
        if (stats != null) writes.add(stats.onQuit(player));
        if (profile != null && profile.isDirty()) writes.add(repository.save(uuid, profile.snapshot()));
        CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, error) -> plugin.getNetwork().releaseProfile(uuid));
    }

    // ---- using profiles ----

    /** The player's profile, or null while it is still loading (or if loading failed). */
    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    public PlayerProfile get(Player player) {
        return profiles.get(player.getUniqueId());
    }

    public boolean isLoaded(UUID uuid) {
        return profiles.containsKey(uuid);
    }

    /** Writes this profile now (asynchronously) if anything changed - call after a purchase or equip so it survives a crash. */
    public CompletableFuture<Void> save(PlayerProfile profile) {
        if (!profile.isDirty()) return CompletableFuture.completedFuture(null);
        return repository.save(profile.uuid(), profile.snapshot()).whenComplete((ignored, error) -> {
            if (error == null) return;
            profile.markDirty(); // try again on the next autosave
            plugin.getLogger().log(Level.WARNING, "Could not save the profile of " + profile.uuid() + " - will retry", error);
        });
    }

    private void saveDirty() {
        for (PlayerProfile profile : profiles.values()) save(profile);
    }

    /** Admin: erase a player's profile (owned items, equipped items, flags). Online players get a fresh one. */
    public CompletableFuture<Void> reset(UUID uuid) {
        PlayerProfile online = profiles.get(uuid);
        if (online != null) {
            profiles.put(uuid, new PlayerProfile(uuid, PlayerProfile.Data.empty(), true));
            profiles.get(uuid).setField(PlayerProfile.FIRST_JOIN, System.currentTimeMillis());
        }
        return repository.delete(uuid);
    }
}
