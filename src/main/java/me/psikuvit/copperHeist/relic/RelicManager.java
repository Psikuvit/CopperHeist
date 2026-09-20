package me.psikuvit.copperHeist.relic;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.RelicLostEvent;
import me.psikuvit.copperHeist.event.RelicPickupEvent;
import me.psikuvit.copperHeist.event.RelicSpawnEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.loot.LootTierDefinition;
import me.psikuvit.copperHeist.task.RelicSpawnTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The timed Relic spawn: a high-value item that isn't part of the
 * normal weighted loot roll, announced globally before it appears, and
 * flows through the exact same pickup -> dock -> golem -> vault pipeline as
 * any other loot item once it's on the ground. This class only owns the
 * spawn timing/warning and the holder's glow+slowness while carrying it.
 */
public class RelicManager {

    private final CopperHeist plugin;
    private final Game game;

    private BukkitTask task;
    private Item groundEntity;
    private Player holder;
    private int secondsUntilSpawn;
    private boolean warned;
    private List<Integer> schedule = List.of(180);
    private int scheduleIndex;
    private boolean replayingLost;
    private int resumeSeconds;

    public RelicManager(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start() {
        schedule = game.settings().getIntegerList("relic.spawn-times");
        if (schedule.isEmpty()) schedule = List.of(180);
        scheduleIndex = 0;
        replayingLost = false;
        secondsUntilSpawn = schedule.getFirst();
        warned = false;
        task = new RelicSpawnTask(this).runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (groundEntity != null && !groundEntity.isDead()) groundEntity.remove();
        if (holder != null) clearHolderEffects(holder);
        groundEntity = null;
        holder = null;
    }

    public Player getHolder() {
        return holder;
    }

    public int getSecondsUntilSpawn() {
        return secondsUntilSpawn;
    }

    public void tick() {
        if (!game.isActive()) return;

        if (groundEntity != null) {
            if (groundEntity.isDead()) groundEntity = null; // picked up (onPickedUp already handled it)
            else return; // still sitting unclaimed - don't spawn another
        }

        if (holder != null) {
            if (isCarryingRelic(holder)) return; // still actively held - don't spawn another
            clearHolderEffects(holder);
            holder = null;
            // falls through to resume the countdown for the next one
        }

        if (secondsUntilSpawn < 0) return; // every scheduled relic has already spawned

        secondsUntilSpawn--;
        if (!warned && secondsUntilSpawn <= 10) {
            warned = true;
            broadcastWarning();
        }
        if (secondsUntilSpawn <= 0) {
            spawnRelic();
            warned = false;
            secondsUntilSpawn = nextCountdown();
        }
    }

    /** After a relic spawns: resume an interrupted countdown if that was a lost-relic respawn, else wait for the next scheduled time. */
    private int nextCountdown() {
        if (replayingLost) {
            replayingLost = false;
            return resumeSeconds;
        }
        scheduleIndex++;
        if (scheduleIndex >= schedule.size()) return -1;
        return schedule.get(scheduleIndex) - schedule.get(scheduleIndex - 1);
    }

    private boolean isCarryingRelic(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (LootItem.isRelic(item)) return true;
        }
        return false;
    }

    /** Debug: spawns the relic right now unless one is already on the ground or being carried. */
    public boolean forceSpawn() {
        if (groundEntity != null || holder != null) return false;
        spawnRelic();
        return true;
    }

    private void spawnRelic() {
        List<Location> points = game.getArena().getRelicPoints();
        if (points.isEmpty()) return;
        Location point = points.get(ThreadLocalRandom.current().nextInt(points.size()));
        if (point.getWorld() == null) return;

        LootTierDefinition relicTier = LootItem.tiers().relicTier();
        if (relicTier == null) return;
        ItemStack stack = LootItem.create(relicTier, game.getMatchId());
        Item entity = point.getWorld().dropItem(point.clone().add(0, 0.5, 0), stack);
        entity.setUnlimitedLifetime(true);
        entity.setGlowing(true);
        groundEntity = entity;

        for (Player player : game.onlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        }
        Bukkit.getPluginManager().callEvent(new RelicSpawnEvent(game, point));
    }

    private void broadcastWarning() {
        Component subtitle = plugin.getMessageService().get("relic.incoming");
        for (Player player : game.onlinePlayers()) {
            player.showTitle(Title.title(Component.empty(), subtitle,
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.2f);
        }
    }

    /** Called by LootListener when a tagged relic item is picked up. */
    public void onPickedUp(Player player) {
        holder = player;
        groundEntity = null;
        player.setGlowing(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 0, true, false));
        Bukkit.getPluginManager().callEvent(new RelicPickupEvent(game, player));
    }

    /** Called by LootListener when the holder dies carrying it - skips the normal ground-drop, respawns fast and silently instead. */
    public void onLost(Player player) {
        clearHolderEffects(player);
        holder = null;
        groundEntity = null;
        warned = true; // no 10s "surfacing" warning for an emergency respawn
        if (!replayingLost) {
            resumeSeconds = secondsUntilSpawn;
            replayingLost = true;
        }
        secondsUntilSpawn = game.settings().getInt("relic.lost-respawn-seconds", 30);
        Bukkit.getPluginManager().callEvent(new RelicLostEvent(game, player));
    }

    private void clearHolderEffects(Player player) {
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
}
