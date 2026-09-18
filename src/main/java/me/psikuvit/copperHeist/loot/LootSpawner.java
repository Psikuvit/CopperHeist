package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Refills the arena's loot points. Each empty point gets a
 * respawn-seconds timer the moment it's noticed empty, rather than a fixed
 * schedule, so picking loot up resets that point's own clock.
 */
public class LootSpawner {

    private final Game game;
    private final Map<Location, Item> active = new HashMap<>();
    private final Map<Location, Long> nextSpawnAt = new HashMap<>();
    private BukkitTask task;

    public LootSpawner(Game game) {
        this.game = game;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(game.getPlugin(), this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Item item : active.values()) {
            if (item != null && !item.isDead()) item.remove();
        }
        active.clear();
        nextSpawnAt.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long respawnMillis = game.getPlugin().getConfig().getLong("loot.respawn-seconds", 35) * 1000L;
        for (Location point : game.getArena().getLootPoints()) {
            Item existing = active.get(point);
            if (existing != null && !existing.isDead()) continue;
            active.remove(point);

            Long eligible = nextSpawnAt.get(point);
            if (eligible == null) {
                nextSpawnAt.put(point, now + respawnMillis);
                continue;
            }
            if (now >= eligible) {
                spawnAt(point);
                nextSpawnAt.remove(point);
            }
        }
    }

    private void spawnAt(Location point) {
        if (point.getWorld() == null) return;
        LootItem.Tier tier = LootItem.randomTier(ThreadLocalRandom.current());
        ItemStack stack = LootItem.create(tier, game.getMatchId());
        Item item = point.getWorld().dropItem(point.clone().add(0.5, 0.5, 0.5), stack);
        item.setUnlimitedLifetime(true);
        item.setVelocity(new Vector(0, 0, 0));
        active.put(point, item);
    }
}
