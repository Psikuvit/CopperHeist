package me.psikuvit.copperHeist.golem;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.event.LootDeliveredEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.game.GameTeam;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.task.GolemRespawnTask;
import me.psikuvit.copperHeist.util.Cooldowns;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every golem belonging to one running {@link Game}: spawning, the
 * dock/vault chest interactions DeliveryGoal calls back into, oxidation
 * speed lookup, scraping, stun/death handling and respawn.
 */
public class GolemManager {

    private final CopperHeist plugin;
    private final Game game;
    private final Map<UUID, HeistGolem> golems = new HashMap<>();
    private final Cooldowns scrapeCooldowns = new Cooldowns();
    private final GolemEffects effects;

    public GolemManager(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
        this.effects = new GolemEffects(plugin.settings());
    }

    public GolemEffects effects() {
        return effects;
    }

    public Collection<HeistGolem> all() {
        return golems.values();
    }

    public HeistGolem get(UUID entityId) {
        return golems.get(entityId);
    }

    public void spawnStarting(Team team) {
        int starting = plugin.settings().getInt("golems.starting", 2);
        for (int i = 0; i < starting; i++) spawnOne(team);
    }

    public HeistGolem spawnOne(Team team) {
        Arena.TeamSite site = game.getArena().site(team);
        if (site.golemIdle == null || site.waypoints.isEmpty() || site.golemIdle.getWorld() == null) {
            plugin.getLogger().warning("Cannot spawn golem for " + team + ": arena not fully set up");
            return null;
        }

        Location spawnLoc = site.golemIdle.clone();
        CopperGolem entity = (CopperGolem) spawnLoc.getWorld().spawnEntity(
                spawnLoc, EntityType.COPPER_GOLEM, CreatureSpawnEvent.SpawnReason.CUSTOM);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.customName(Component.text(team.displayName() + " Golem", team.color()));
        entity.setCustomNameVisible(false);
        entity.setWeatheringState(WeatheringCopperState.UNAFFECTED);
        Pdc.set(entity, PdcKeys.MATCH_ID, game.getMatchId());
        Pdc.set(entity, PdcKeys.GOLEM_TEAM, team.name());

        double health = plugin.settings().getDouble("golems.health", 30.0);
        AttributeInstance healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(health);
        entity.setHealth(health);

        Bukkit.getMobGoals().removeAllGoals(entity);

        HeistGolem golem = new HeistGolem(entity, team, site.golemIdle.clone(), new ArrayList<>(site.waypoints), this);
        golem.setStageDurationMillis(rollStageDuration());
        golems.put(entity.getUniqueId(), golem);
        plugin.getGameManager().registerGolem(game, golem);
        game.getTeam(team).getGolems().add(golem);

        Bukkit.getMobGoals().addGoal(entity, 0, new DeliveryGoal(golem));

        spawnLabel(golem);
        return golem;
    }

    private void spawnLabel(HeistGolem golem) {
        Location above = golem.getEntity().getLocation().add(0, 2.3, 0);
        TextDisplay display = above.getWorld().spawn(above, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.text(Component.empty());
            d.setPersistent(true);
        });
        golem.setLabel(display);
    }

    /** Called from the game's periodic UI tick; labels don't ride the golem, just follow it. */
    public void syncLabels() {
        for (HeistGolem golem : golems.values()) {
            if (golem.getLabel() != null && !golem.getLabel().isDead()) {
                golem.getLabel().teleport(golem.getEntity().getLocation().add(0, 2.3, 0));
            }
            updateLabel(golem);
        }
    }

    public void updateLabel(HeistGolem golem) {
        TextDisplay label = golem.getLabel();
        if (label == null || label.isDead()) return;
        if (golem.isCarrying()) {
            int value = LootItem.getValue(golem.getCarried()) * golem.getCarried().getAmount();
            label.text(Component.text("[" + value + "]", golem.getTeam().color()));
        } else if (golem.getEntity().getWeatheringState() == WeatheringCopperState.OXIDIZED) {
            label.text(plugin.getMessageService().get("golem.oxidized-label"));
        } else {
            label.text(Component.empty());
        }
    }

    public Settings settings() {
        return plugin.settings();
    }

    public boolean isInBounds(Location location) {
        return game.getArena().isInBounds(location);
    }

    public double currentSpeed(HeistGolem golem) {
        double base = plugin.settings().getDouble("golems.base-speed", 0.3);
        double multiplier = switch (golem.getEntity().getWeatheringState()) {
            case UNAFFECTED -> plugin.settings().getDouble("golems.speed-multipliers.fresh", 1.0);
            case EXPOSED -> plugin.settings().getDouble("golems.speed-multipliers.exposed", 0.8);
            case WEATHERED -> plugin.settings().getDouble("golems.speed-multipliers.weathered", 0.55);
            case OXIDIZED -> plugin.settings().getDouble("golems.speed-multipliers.oxidized", 0.0);
        };
        return base * multiplier;
    }

    public ItemStack tryTakeFromDock(Team team) {
        Arena.TeamSite site = game.getArena().site(team);
        for (Location loc : site.dockChests) {
            if (loc.getWorld() == null) continue;
            BlockState state = loc.getBlock().getState();
            if (!(state instanceof Chest chest)) continue;
            Inventory inv = chest.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item == null) continue;
                if (LootItem.isLoot(item) && game.getMatchId().equals(LootItem.getMatchId(item))) {
                    inv.setItem(i, null);
                    return item;
                }
            }
        }
        return null;
    }

    public void deposit(HeistGolem golem) {
        ItemStack carried = golem.getCarried();
        if (carried == null) return;
        String lootId = Pdc.get(carried, PdcKeys.LOOT_ID);
        if (lootId != null && !game.markLootScored(lootId)) {
            plugin.getLogger().warning("Ignored a duplicate loot item (" + lootId + ") delivered by a " + golem.getTeam() + " golem");
            golem.setCarried(null);
            updateLabel(golem);
            return;
        }
        Arena.TeamSite site = game.getArena().site(golem.getTeam());
        boolean placed = false;
        for (Location loc : site.vaultChests) {
            if (loc.getWorld() == null) continue;
            BlockState state = loc.getBlock().getState();
            if (!(state instanceof Chest chest)) continue;
            Map<Integer, ItemStack> leftover = chest.getInventory().addItem(carried.clone());
            if (leftover.isEmpty()) {
                placed = true;
                break;
            }
        }
        if (!placed) {
            plugin.getLogger().warning("Vault chests for " + golem.getTeam() + " are full, loot still scored");
        }

        int value = LootItem.getValue(carried) * carried.getAmount();
        if (game.isFinalRushActive()) {
            value = (int) Math.round(value * plugin.settings().getDouble("final-rush.loot-multiplier", 2.0));
        }
        GameTeam gameTeam = game.getTeam(golem.getTeam());
        gameTeam.addScore(value);
        gameTeam.markDelivery();
        boolean relic = LootItem.isRelic(carried);
        if (relic) gameTeam.addRelicDelivered();
        UUID carrier = LootItem.getLastCarrier(carried);
        GamePlayer carrierGp = carrier == null ? null : game.getGamePlayer(carrier);
        if (carrierGp != null) {
            carrierGp.addDelivered(value);
            if (relic) carrierGp.addRelicDelivered();
        }
        Bukkit.getPluginManager().callEvent(new LootDeliveredEvent(game, golem.getTeam(), carried, value));

        golem.setCarried(null);
        updateLabel(golem);
    }

    public void onDamaged(HeistGolem golem, Player attacker) {
        if (golem.isStunImmune()) return;
        dropCarried(golem, golem.getEntity().getLocation());
        int stunSeconds = plugin.settings().getInt("golems.stun-seconds", 3);
        int immunitySeconds = plugin.settings().getInt("golems.stun-immunity-seconds", 5);
        golem.stun(stunSeconds, immunitySeconds);
        effects.stunned(golem.getEntity().getLocation());
    }

    public void onDeath(HeistGolem golem) {
        dropCarried(golem, golem.getEntity().getLocation());
        golems.remove(golem.getEntity().getUniqueId());
        plugin.getGameManager().unregisterGolem(golem.getEntity().getUniqueId());
        game.getTeam(golem.getTeam()).getGolems().remove(golem);
        if (golem.getLabel() != null) golem.getLabel().remove();

        int respawnSeconds = plugin.settings().getInt("golems.auto-respawn-seconds", 60);
        new GolemRespawnTask(this, golem.getTeam()).runTaskLater(plugin, respawnSeconds * 20L);
    }

    public void respawnIfShort(Team team) {
        if (!game.isActive()) return;
        int starting = plugin.settings().getInt("golems.starting", 2);
        if (game.getTeam(team).getGolems().size() < starting) spawnOne(team);
    }

    private void dropCarried(HeistGolem golem, Location location) {
        if (!golem.isCarrying()) return;
        ItemStack carried = golem.getCarried();
        LootItem.setLastTeam(carried, golem.getTeam());
        location.getWorld().dropItemNaturally(location, carried);
        golem.setCarried(null);
        updateLabel(golem);
    }

    /** cooldownMultiplier lets a role (Mechanic) discount its own scrape cooldown; callers with no such perk pass 1.0. */
    public boolean scrape(HeistGolem golem, double cooldownMultiplier) {
        if (golem.isWaxed()) return false;
        WeatheringCopperState current = golem.getEntity().getWeatheringState();
        if (current == WeatheringCopperState.UNAFFECTED) return false;
        UUID id = golem.getEntity().getUniqueId();
        if (!scrapeCooldowns.isReady(id)) return false;

        golem.getEntity().setWeatheringState(previousStage(current));
        golem.setStageChangedAtMillis(System.currentTimeMillis());
        golem.setStageDurationMillis(rollStageDuration());
        updateLabel(golem);

        int cooldown = (int) (plugin.settings().getInt("golems.scrape-cooldown-seconds", 20) * cooldownMultiplier);
        scrapeCooldowns.set(id, cooldown);
        effects.scraped(golem.getEntity().getLocation());
        return true;
    }

    public long scrapeCooldownRemaining(UUID golemId) {
        return scrapeCooldowns.remainingSeconds(golemId);
    }

    /** Freezes a golem's current oxidation stage - no aging, and it can't be scraped - for golems.wax-duration-seconds. */
    public void wax(HeistGolem golem) {
        golem.getEntity().setOxidizing(CopperGolem.Oxidizing.waxed());
        int duration = plugin.settings().getInt("golems.wax-duration-seconds", 180);
        golem.setWaxedUntilMillis(System.currentTimeMillis() + duration * 1000L);
        effects.waxed(golem.getEntity().getLocation());
        updateLabel(golem);
    }

    /** Un-waxes a golem and resets its aging clock, whether the wax expired naturally or was cleared early (Storm Rod). */
    public void unwax(HeistGolem golem) {
        golem.getEntity().setOxidizing(CopperGolem.Oxidizing.unset());
        golem.setWaxedUntilMillis(0);
        golem.setStageChangedAtMillis(System.currentTimeMillis());
        updateLabel(golem);
    }

    /** Storm Rod: resets every own golem within 8 blocks of the team's dock to Fresh, and zaps every nearby player. */
    public void stormReset(Team team) {
        Arena.TeamSite site = game.getArena().site(team);
        if (site.golemIdle == null || site.golemIdle.getWorld() == null) return;
        Location center = site.golemIdle;
        double radius = plugin.settings().getDouble("golems.storm-rod.radius", 8.0);
        double radiusSquared = radius * radius;
        double zapDamage = plugin.settings().getDouble("golems.storm-rod.damage", 6.0);

        center.getWorld().strikeLightningEffect(center);

        for (HeistGolem golem : new ArrayList<>(game.getTeam(team).getGolems())) {
            if (golem.getEntity().getLocation().distanceSquared(center) <= radiusSquared) {
                golem.getEntity().setWeatheringState(WeatheringCopperState.UNAFFECTED);
                unwax(golem);
            }
        }
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSquared) {
                player.damage(zapDamage);
            }
        }
    }

    public static WeatheringCopperState previousStage(WeatheringCopperState state) {
        int ordinal = Math.max(0, state.ordinal() - 1);
        return WeatheringCopperState.values()[ordinal];
    }

    public static WeatheringCopperState nextStage(WeatheringCopperState state) {
        int ordinal = Math.min(WeatheringCopperState.values().length - 1, state.ordinal() + 1);
        return WeatheringCopperState.values()[ordinal];
    }

    private long rollStageDuration() {
        long base = plugin.settings().getLong("golems.aging-seconds", 210) * 1000L;
        long jitter = plugin.settings().getLong("golems.aging-jitter-seconds", 20) * 1000L;
        long jittered = base + (long) ((Math.random() * 2 - 1) * jitter);
        return Math.max(1000L, jittered);
    }

    public void despawnAll() {
        for (HeistGolem golem : new ArrayList<>(golems.values())) {
            if (golem.getLabel() != null) golem.getLabel().remove();
            golem.getEntity().remove();
            plugin.getGameManager().unregisterGolem(golem.getEntity().getUniqueId());
        }
        golems.clear();
    }
}
