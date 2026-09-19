package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.task.LootRefillTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
        task = new LootRefillTask(this).runTaskTimer(game.getPlugin(), 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Item item : active.values()) {
            if (item != null && !item.isDead()) item.remove();
        }
        active.clear();
        nextSpawnAt.clear();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Arena.LootZone zone : Arena.LootZone.values()) {
            long respawnMillis = respawnMillis(zone);
            for (Location point : game.getArena().getLootPoints(zone)) {
                Item existing = active.get(point);
                if (existing != null && !existing.isDead()) continue;
                active.remove(point);

                Long eligible = nextSpawnAt.get(point);
                if (eligible == null) {
                    nextSpawnAt.put(point, now + respawnMillis);
                    continue;
                }
                if (now >= eligible) {
                    spawnAt(point, zone);
                    nextSpawnAt.remove(point);
                }
            }
        }
        if (game.feature("unclaimed-bonus")) applyUnclaimedBonus();
    }

    /** Caches refill slower than the central zone, and everything refills faster during Final Rush. */
    private long respawnMillis(Arena.LootZone zone) {
        var config = game.settings();
        double seconds = zone == Arena.LootZone.CACHE
                ? config.getDouble("loot.cache-respawn-seconds", 60)
                : config.getDouble("loot.respawn-seconds", 35);
        if (game.isFinalRushActive()) {
            seconds *= config.getDouble("final-rush.loot-respawn-multiplier", 0.5);
        }
        return (long) (seconds * 1000L);
    }

    /** Loot left lying around for a while gains value over time, to pull turtling teams out. */
    private void applyUnclaimedBonus() {
        var config = game.settings();
        int after = config.getInt("loot.unclaimed-bonus.after-seconds", 60);
        int every = Math.max(1, config.getInt("loot.unclaimed-bonus.every-seconds", 30));
        int max = config.getInt("loot.unclaimed-bonus.max-bonus", 5);
        World world = game.getArena().getWorld();
        if (world == null) return;

        for (Item entity : world.getEntitiesByClass(Item.class)) {
            ItemStack stack = entity.getItemStack();
            if (!LootItem.isLoot(stack) || !game.getMatchId().equals(LootItem.getMatchId(stack))) continue;
            LootTierDefinition tier = LootItem.getTier(stack);
            if (tier == null || tier.relic()) continue;

            int ageSeconds = entity.getTicksLived() / 20;
            int bonus = ageSeconds < after ? 0 : Math.min(max, (ageSeconds - after) / every + 1);
            if (LootItem.getValue(stack) == tier.value() + bonus) continue;
            LootItem.setValue(stack, tier.value() + bonus);
            entity.setItemStack(stack);
        }
    }

    private void spawnAt(Location point, Arena.LootZone zone) {
        if (point.getWorld() == null) return;
        LootTierDefinition tier = LootItem.tiers().roll(ThreadLocalRandom.current(), zone);
        if (tier == null) return;
        ItemStack stack = LootItem.create(tier, game.getMatchId());
        Item item = point.getWorld().dropItem(point.clone().add(0.5, 0.5, 0.5), stack);
        item.setUnlimitedLifetime(true);
        item.setVelocity(new Vector(0, 0, 0));
        active.put(point, item);
    }
}
