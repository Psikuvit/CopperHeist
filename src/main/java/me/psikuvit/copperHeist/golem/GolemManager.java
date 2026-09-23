package me.psikuvit.copperHeist.golem;

import com.destroystokyo.paper.entity.Pathfinder;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.arena.Region;
import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.event.LootDeliveredEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.GameTeam;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.golem.GolemDebug.Category;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.task.golem.GolemRespawnTask;
import me.psikuvit.copperHeist.util.Cooldowns;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns every golem belonging to one running {@link Game}.
 *
 * The golems themselves are <b>vanilla</b>: they wander, look for copper chests, take an item out, carry it in their hand and
 * put it into a normal chest that is empty or already holds that item - with vanilla's own pathfinding, chest opening and
 * animations. In this game the dock is a copper chest and the vault is a normal chest, so a golem delivers loot simply by doing
 * what a copper golem does.
 *
 * The plugin only adds what vanilla can't know about - <b>safety guards</b>, run every tick:
 * <ul>
 * <li>Team guard: loot a golem takes out of the other team's dock, or puts into the other team's vault, is put back
 *     (vanilla golems will happily use any chest in range).</li>
 * <li>Scoring: loot that turns up in a team's vault, put there by that team's golem, is scored once.</li>
 * <li>Stuck-item assist: a golem that has held loot for too long (every vault chest holds other item types, so vanilla has
 *     nowhere to put it) has the loot placed in its vault for it.</li>
 * <li>Foreign items: anything in a golem's hand that isn't loot goes back to its dock.</li>
 * <li>Leash: a golem outside the arena, or inside the enemy base, is sent home.</li>
 * <li>Game rules vanilla has no idea about: stun (AI off for a few seconds), full oxidation (AI off - a frozen statue until
 *     scraped, and vanilla can't turn it into a statue block), and oxidation slowing the golem down.</li>
 * </ul>
 * Everything is reported through {@link GolemDebug}.
 */
public class GolemManager {

    private record Holder(HeistGolem golem, long seenAtMillis) {
    }

    private static final long HOLDER_MEMORY_MILLIS = 15_000;

    private final CopperHeist plugin;
    private final Game game;
    private final Map<UUID, HeistGolem> golems = new HashMap<>();
    private final Cooldowns scrapeCooldowns = new Cooldowns();
    private final GolemEffects effects;
    private final Map<Team, Integer> numbers = new EnumMap<>(Team.class);
    private final Map<String, Set<String>> seenLoot = new HashMap<>();
    private final Map<String, Holder> holders = new HashMap<>();
    private BukkitTask tickTask;
    private long tickCount;

    public GolemManager(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
        this.effects = new GolemEffects(game.settings());
    }

    public GolemDebug debug() {
        return plugin.getGolemDebug();
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

    // ---- spawning ----

    public void spawnStarting(Team team) {
        int starting = game.settings().getInt("golems.starting", 2);
        for (int i = 0; i < starting; i++) spawnOne(team);
    }

    public HeistGolem spawnOne(Team team) {
        Arena.TeamSite site = game.getArena().site(team);
        if (site.golemIdle == null || site.golemIdle.getWorld() == null) {
            plugin.getLogger().warning("Cannot spawn golem for " + team + ": the arena has no golem idle point");
            return null;
        }

        Location spawnLoc = site.golemIdle.clone();
        CopperGolem entity = (CopperGolem) spawnLoc.getWorld().spawnEntity(
                spawnLoc, EntityType.COPPER_GOLEM, CreatureSpawnEvent.SpawnReason.CUSTOM);
        entity.setRemoveWhenFarAway(false);
        entity.customName(Component.text(team.displayName() + " Golem", team.color()));
        entity.setCustomNameVisible(false);
        entity.setWeatheringState(WeatheringCopperState.UNAFFECTED);
        Pdc.set(entity, PdcKeys.MATCH_ID, game.getMatchId());
        Pdc.set(entity, PdcKeys.GOLEM_TEAM, team.name());

        double health = game.settings().getDouble("golems.health", 12.0);
        AttributeInstance healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(health);
        entity.setHealth(health);

        HeistGolem golem = new HeistGolem(entity, team, site.golemIdle.clone(), new ArrayList<>(site.waypoints));
        golem.setNumber(numbers.merge(team, 1, Integer::sum));
        golem.setStageDurationMillis(rollStageDuration());
        golems.put(entity.getUniqueId(), golem);
        plugin.getGameManager().registerGolem(game, golem);
        game.getTeam(team).getGolems().add(golem);
        applySpeed(golem);
        startTicking();
        debug().log(golem, Category.STATE, "spawned at " + GolemDebug.at(spawnLoc) + " with vanilla AI");

        spawnLabel(golem);
        return golem;
    }

    // ---- labels ----

    private void spawnLabel(HeistGolem golem) {
        Location at = golem.getEntity().getLocation();
        float lift = (float) game.settings().getDouble("golems.label-height", 0.7);
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.text(Component.empty());
            // The label rides the golem as a passenger, so the client moves it together with the golem every frame;
            // the translation lifts it above the golem's head.
            d.setTransformation(new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f(), new Vector3f(1, 1, 1), new AxisAngle4f()));
        });
        golem.setLabel(display);
        golem.getEntity().addPassenger(display);
    }

    /** Called from the game's periodic UI tick: keeps every label mounted and its text up to date. */
    public void syncLabels() {
        for (HeistGolem golem : golems.values()) {
            TextDisplay label = golem.getLabel();
            if (label != null && !label.isDead() && !golem.getEntity().getPassengers().contains(label)) {
                label.teleport(golem.getEntity().getLocation());
                golem.getEntity().addPassenger(label);
            }
            updateLabel(golem);
        }
    }

    public void updateLabel(HeistGolem golem) {
        TextDisplay label = golem.getLabel();
        if (label == null || label.isDead()) return;
        Component text;
        ItemStack carried = golem.getCarried();
        if (carried != null) {
            int value = LootItem.getValue(carried) * carried.getAmount();
            text = Component.text("[" + value + "]", golem.getTeam().color());
        } else if (golem.getEntity().getWeatheringState() == WeatheringCopperState.OXIDIZED) {
            text = plugin.getMessageService().get("golem.oxidized-label");
        } else {
            text = Component.empty();
        }
        if (debug().visuals()) {
            text = text.appendNewline().append(Component.text(golem.debugName() + " " + golem.getEntity().getGolemState(), NamedTextColor.YELLOW));
        }
        label.text(text);
    }

    public Settings settings() {
        return game.settings();
    }

    public boolean isInBounds(Location location) {
        return game.getArena().isInBounds(location);
    }

    // ---- speed ----

    /** How much of its normal speed a golem has at its current oxidation stage (0 when fully oxidized). */
    public double speedMultiplier(HeistGolem golem) {
        return switch (golem.getEntity().getWeatheringState()) {
            case UNAFFECTED -> game.settings().getDouble("golems.speed-multipliers.fresh", 1.0);
            case EXPOSED -> game.settings().getDouble("golems.speed-multipliers.exposed", 0.8);
            case WEATHERED -> game.settings().getDouble("golems.speed-multipliers.weathered", 0.55);
            case OXIDIZED -> game.settings().getDouble("golems.speed-multipliers.oxidized", 0.0);
        };
    }

    /** Vanilla walks at its movement speed attribute, so oxidation slows a golem by lowering that. */
    private void applySpeed(HeistGolem golem) {
        AttributeInstance speed = golem.getEntity().getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double wanted = game.settings().getDouble("golems.base-speed", 0.2) * speedMultiplier(golem);
        if (Math.abs(speed.getBaseValue() - wanted) > 1.0E-6) {
            speed.setBaseValue(wanted);
            debug().log(golem, Category.STATE, String.format(Locale.ROOT, "speed set to %.3f (%s)", wanted, golem.getEntity().getWeatheringState()));
        }
    }

    // ---- the per-tick watch ----

    /**
     * Golem skins: each team's golems wear the rarest golem cosmetic equipped by anyone on that team (an aura, a hat ...), played every half
     * second by the cosmetics service, which skips anything that is off, missing or broken.
     */
    private void playGolemCosmetics() {
        var cosmetics = plugin.getCosmetics();
        if (!cosmetics.enabled()) return;
        var viewers = cosmetics.viewers(game);
        for (Team team : Team.values()) {
            var pick = cosmetics.bestForTeam(game, team, CosmeticCategory.GOLEM);
            if (pick == null) continue;
            for (HeistGolem golem : golems.values()) {
                if (golem.getTeam() != team || golem.getEntity().isDead()) continue;
                cosmetics.playPick(pick, golem.getEntity().getLocation(), golem.getEntity(), viewers);
            }
        }
    }

    /** Removes what cosmetics attached to a golem (a hat rides it as a passenger, like the label). */
    private void removeCosmeticPassengers(HeistGolem golem) {
        for (Entity passenger : new ArrayList<>(golem.getEntity().getPassengers())) {
            if (Pdc.has(passenger, PdcKeys.COSMETIC)) passenger.remove();
        }
    }

    private void startTicking() {
        if (tickTask != null) return;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        tickCount++;
        for (HeistGolem golem : new ArrayList<>(golems.values())) {
            if (golem.getEntity().isDead()) continue;
            try {
                watchGolem(golem, now);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Golem " + golem.debugName() + " failed its watch", ex);
            }
        }
        if (tickCount % 10 == 0 && game.isActive()) playGolemCosmetics();
        if (tickCount % 5 == 0 && game.isActive()) {
            try {
                watchChests(now);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Chest watch failed", ex);
            }
        }
    }

    private void watchGolem(HeistGolem golem, long now) {
        CopperGolem entity = golem.getEntity();

        // Game rules vanilla doesn't have: stunned or fully oxidized golems stand still (AI off), everything else runs vanilla AI.
        boolean oxidized = entity.getWeatheringState() == WeatheringCopperState.OXIDIZED;
        boolean stunned = golem.isStunned();
        boolean wantAi = !oxidized && !stunned;
        if (entity.hasAI() != wantAi) {
            entity.setAI(wantAi);
            debug().log(golem, Category.STATE, "AI " + (wantAi ? "on" : "off") + (oxidized ? " (fully oxidized)" : stunned ? " (stunned)" : ""));
        }
        if (stunned && tickCount % 10 == 0) effects.dizzy(entity.getLocation());
        applySpeed(golem);

        // Leash guards.
        if (!isInBounds(entity.getLocation())) {
            sendHome(golem, "left the arena bounds at " + GolemDebug.at(entity.getLocation()));
            return;
        }
        if (game.settings().getBoolean("golems.guards.enemy-base", true)) {
            Region enemyBase = game.getArena().site(golem.getTeam().opposite()).base();
            if (enemyBase != null && enemyBase.contains(entity.getLocation())) {
                returnHeldItem(golem, "inside the enemy base");
                sendHome(golem, "entered the enemy base");
                return;
            }
        }

        // Observe vanilla: animation state and what is in its hand.
        CopperGolem.State state = entity.getGolemState();
        if (state != golem.lastState()) {
            debug().log(golem, Category.STATE, golem.lastState() + " -> " + state + " at " + GolemDebug.at(entity.getLocation())
                    + " (nearest chest: " + nearestChestDescription(entity.getLocation()) + ")");
            golem.lastState(state);
        }
        watchHand(golem, now);
    }

    private void watchHand(HeistGolem golem, long now) {
        ItemStack hand = golem.getEntity().getEquipment().getItemInMainHand();
        String lootId = LootItem.isLoot(hand) ? Pdc.get(hand, PdcKeys.LOOT_ID) : null;

        if (!hand.getType().isAir() && !LootItem.isLoot(hand) && game.settings().getBoolean("golems.guards.only-loot", true)) {
            returnHeldItem(golem, "held something that isn't loot (" + hand.getType() + ")");
            return;
        }

        String before = golem.holdingId();
        if (lootId != null) {
            holders.put(lootId, new Holder(golem, now));
            if (!lootId.equals(before)) {
                golem.holding(lootId, now);
                debug().log(golem, Category.HAND, "now holding " + hand.getAmount() + "x " + hand.getType() + " (value "
                        + LootItem.getValue(hand) * hand.getAmount() + ") at " + GolemDebug.at(golem.getEntity().getLocation()));
                updateLabel(golem);
            } else {
                assistIfStuck(golem, hand, now);
            }
        } else if (before != null) {
            golem.holding(null, now);
            debug().log(golem, Category.HAND, "hand emptied (was holding loot " + shortId(before) + ")");
            updateLabel(golem);
        }
    }

    /** Vanilla only puts an item in a chest that is empty or already holds that item; if none exists the golem keeps it forever. */
    private void assistIfStuck(HeistGolem golem, ItemStack hand, long now) {
        long timeout = (long) (game.settings().getDouble("golems.guards.hold-timeout-seconds", 45) * 1000);
        if (timeout <= 0 || now - golem.holdingSinceMillis() < timeout) return;
        for (Location vault : game.getArena().site(golem.getTeam()).vaultChests) {
            Container container = containerAt(vault);
            if (container == null) continue;
            if (container.getInventory().addItem(hand.clone()).isEmpty()) {
                golem.setCarried(null);
                debug().log(golem, Category.GUARD, "held " + hand.getType() + " for " + (now - golem.holdingSinceMillis()) / 1000
                        + "s with nowhere to put it - placed it in the vault chest at " + GolemDebug.at(vault));
                return;
            }
        }
        if (now - golem.lastGuardNoticeMillis() > 30_000) {
            golem.lastGuardNoticeMillis(now);
            debug().log(golem, Category.GUARD, "has held " + hand.getType() + " for " + (now - golem.holdingSinceMillis()) / 1000
                    + "s and every vault chest is full");
        }
    }

    // ---- guards ----

    private void sendHome(HeistGolem golem, String reason) {
        debug().log(golem, Category.GUARD, "sent home: " + reason);
        golem.getEntity().teleport(golem.getHome(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /** Puts whatever the golem holds back into its own dock (or drops it there if the dock is full). */
    private void returnHeldItem(HeistGolem golem, String reason) {
        ItemStack hand = golem.getEntity().getEquipment().getItemInMainHand();
        if (hand.getType().isAir()) return;
        golem.setCarried(null);
        giveToDock(golem.getTeam(), hand.clone(), golem.getEntity().getLocation());
        debug().log(golem, Category.GUARD, "took its " + hand.getType() + " away and returned it to the " + golem.getTeam() + " dock: " + reason);
    }

    private void giveToDock(Team team, ItemStack item, Location fallback) {
        for (Location dock : game.getArena().site(team).dockChests) {
            Container container = containerAt(dock);
            if (container != null && container.getInventory().addItem(item.clone()).isEmpty()) return;
        }
        fallback.getWorld().dropItemNaturally(fallback, item);
    }

    // ---- chest watch: team guard and scoring ----

    private void watchChests(long now) {
        holders.values().removeIf(holder -> now - holder.seenAtMillis() > HOLDER_MEMORY_MILLIS);
        for (Team team : Team.values()) {
            Arena.TeamSite site = game.getArena().site(team);
            for (Location dock : site.dockChests) watchDock(team, dock);
            for (Location vault : site.vaultChests) watchVault(team, vault);
        }
    }

    private void watchDock(Team owner, Location loc) {
        Container container = containerAt(loc);
        if (container == null) return;
        Set<String> current = lootIds(container.getInventory());
        Set<String> previous = seenLoot.put(key(loc), current);
        if (previous == null) return;

        Set<String> removed = new HashSet<>(previous);
        removed.removeAll(current);
        for (String id : removed) {
            Holder holder = holders.get(id);
            if (holder == null) {
                debug().log(Category.CHEST, owner + " dock " + GolemDebug.at(loc) + ": loot " + shortId(id) + " left (not taken by a golem)");
            } else if (holder.golem().getTeam() != owner) {
                ItemStack hand = holder.golem().getEntity().getEquipment().getItemInMainHand();
                if (id.equals(Pdc.get(hand, PdcKeys.LOOT_ID))) {
                    holder.golem().setCarried(null);
                    container.getInventory().addItem(hand.clone());
                    holders.remove(id);
                    debug().log(holder.golem(), Category.GUARD, "took loot out of the " + owner + " dock at " + GolemDebug.at(loc)
                            + " - put it back");
                }
            } else {
                debug().log(holder.golem(), Category.CHEST, "took loot " + shortId(id) + " from its dock at " + GolemDebug.at(loc));
            }
        }
        seenLoot.put(key(loc), lootIds(container.getInventory()));
    }

    private void watchVault(Team owner, Location loc) {
        Container container = containerAt(loc);
        if (container == null) return;
        Set<String> current = lootIds(container.getInventory());
        Set<String> previous = seenLoot.put(key(loc), current);
        if (previous == null) return;

        Set<String> added = new HashSet<>(current);
        added.removeAll(previous);
        for (String id : added) {
            Holder holder = holders.get(id);
            ItemStack stack = findStack(container.getInventory(), id);
            if (stack == null) continue;
            if (holder == null) {
                debug().log(Category.CHEST, owner + " vault " + GolemDebug.at(loc) + ": loot " + shortId(id) + " appeared with no golem holding it (not scored)");
            } else if (holder.golem().getTeam() == owner) {
                holders.remove(id);
                debug().log(holder.golem(), Category.CHEST, "delivered " + stack.getAmount() + "x " + stack.getType() + " to its vault at " + GolemDebug.at(loc));
                scoreDelivered(holder.golem(), stack);
            } else {
                container.getInventory().removeItem(stack);
                giveToDock(holder.golem().getTeam(), stack.clone(), loc);
                holders.remove(id);
                debug().log(holder.golem(), Category.GUARD, "put loot into the " + owner + " vault at " + GolemDebug.at(loc) + " - took it back to its own dock");
            }
        }
        seenLoot.put(key(loc), lootIds(container.getInventory()));
    }

    /** Scores a delivery for the golem's team once, however many times the chest is looked at. */
    private void scoreDelivered(HeistGolem golem, ItemStack item) {
        String lootId = Pdc.get(item, PdcKeys.LOOT_ID);
        if (lootId != null && !game.markLootScored(lootId)) {
            plugin.getLogger().warning("Ignored a duplicate loot item (" + lootId + ") delivered by a " + golem.getTeam() + " golem");
            return;
        }
        int value = LootItem.getValue(item) * item.getAmount();
        if (game.isFinalRushActive()) {
            value = (int) Math.round(value * game.settings().getDouble("final-rush.loot-multiplier", 2.0));
        }
        GameTeam gameTeam = game.getTeam(golem.getTeam());
        gameTeam.addScore(value);
        gameTeam.markDelivery();
        boolean relic = LootItem.isRelic(item);
        if (relic) gameTeam.addRelicDelivered();
        UUID carrier = LootItem.getLastCarrier(item);
        GamePlayer carrierGp = carrier == null ? null : game.getGamePlayer(carrier);
        if (carrierGp != null) {
            carrierGp.addDelivered(value);
            if (relic) carrierGp.addRelicDelivered();
        }
        Bukkit.getPluginManager().callEvent(new LootDeliveredEvent(game, golem.getTeam(), item, value));
        updateLabel(golem);
    }

    // ---- chest helpers ----

    private static String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    /** Any chest-like block (copper chest, chest, trapped chest, barrel) as a container, or null if it's gone or its chunk isn't loaded. */
    private Container containerAt(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return null;
        BlockState state = loc.getBlock().getState(false);
        return state instanceof Container container ? container : null;
    }

    private Set<String> lootIds(Inventory inventory) {
        Set<String> ids = new HashSet<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || !LootItem.isLoot(item) || !game.getMatchId().equals(LootItem.getMatchId(item))) continue;
            String id = Pdc.get(item, PdcKeys.LOOT_ID);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private ItemStack findStack(Inventory inventory, String lootId) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && LootItem.isLoot(item) && lootId.equals(Pdc.get(item, PdcKeys.LOOT_ID))) return item;
        }
        return null;
    }

    private String nearestChestDescription(Location from) {
        String best = "none";
        double bestDistance = Double.MAX_VALUE;
        for (Team team : Team.values()) {
            Arena.TeamSite site = game.getArena().site(team);
            for (Location loc : site.dockChests) {
                double d = loc.getWorld() == from.getWorld() ? loc.distanceSquared(from) : Double.MAX_VALUE;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = team + " dock " + GolemDebug.at(loc);
                }
            }
            for (Location loc : site.vaultChests) {
                double d = loc.getWorld() == from.getWorld() ? loc.distanceSquared(from) : Double.MAX_VALUE;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = team + " vault " + GolemDebug.at(loc);
                }
            }
        }
        return bestDistance == Double.MAX_VALUE ? best : best + String.format(Locale.ROOT, " %.1f away", Math.sqrt(bestDistance));
    }

    // ---- debug support ----

    /** Where vanilla's pathfinding is currently sending the golem, or null if it has no path. */
    public Location debugTarget(HeistGolem golem) {
        Pathfinder.PathResult path = golem.getEntity().getPathfinder().getCurrentPath();
        return path == null ? null : path.getFinalPoint();
    }

    /** Text for /ch admin debug dump: every golem of this game plus what the chests hold. */
    public List<String> describeAll() {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (HeistGolem golem : golems.values()) {
            CopperGolem entity = golem.getEntity();
            Location here = entity.getLocation();
            Location target = debugTarget(golem);
            ItemStack carried = golem.getCarried();
            lines.add(golem.debugName() + ": " + entity.getGolemState() + "  AI " + (entity.hasAI() ? "on" : "off")
                    + (golem.isStunned() ? " [STUNNED]" : "") + (entity.getWeatheringState() == WeatheringCopperState.OXIDIZED ? " [OXIDIZED]" : "")
                    + "  " + entity.getWeatheringState());
            lines.add("  at " + GolemDebug.at(here) + "  path-to " + GolemDebug.at(target)
                    + (target == null ? "" : String.format(Locale.ROOT, " (%.1f away)", here.distance(target))));
            lines.add("  hand " + (carried == null ? "nothing" : carried.getAmount() + "x " + carried.getType() + " (value "
                    + LootItem.getValue(carried) * carried.getAmount() + ") for " + (now - golem.holdingSinceMillis()) / 1000 + "s"));
            lines.add("  nearest chest: " + nearestChestDescription(here));
        }
        for (Team team : Team.values()) {
            Arena.TeamSite site = game.getArena().site(team);
            for (Location dock : site.dockChests) lines.add(describeChest(team + " dock", dock));
            for (Location vault : site.vaultChests) lines.add(describeChest(team + " vault", vault));
        }
        return lines;
    }

    private String describeChest(String label, Location loc) {
        Container container = containerAt(loc);
        if (container == null) return label + " " + GolemDebug.at(loc) + ": not loaded or not a container";
        return label + " " + GolemDebug.at(loc) + ": " + loc.getBlock().getType() + ", " + lootIds(container.getInventory()).size() + " loot stack(s)";
    }

    // ---- damage, death, respawn ----

    public void onDamaged(HeistGolem golem, Player attacker) {
        if (golem.isStunImmune()) return;
        dropCarried(golem, golem.getEntity().getLocation());
        int stunSeconds = game.settings().getInt("golems.stun-seconds", 3);
        int immunitySeconds = game.settings().getInt("golems.stun-immunity-seconds", 5);
        golem.stun(stunSeconds, immunitySeconds);
        effects.stunned(golem.getEntity().getLocation());
        debug().log(golem, Category.STATE, "stunned for " + stunSeconds + "s by " + attacker.getName());
    }

    public void onDeath(HeistGolem golem) {
        dropCarried(golem, golem.getEntity().getLocation());
        golems.remove(golem.getEntity().getUniqueId());
        plugin.getGameManager().unregisterGolem(golem.getEntity().getUniqueId());
        game.getTeam(golem.getTeam()).getGolems().remove(golem);
        if (golem.getLabel() != null) golem.getLabel().remove();
        removeCosmeticPassengers(golem);
        debug().log(golem, Category.STATE, "died");

        int respawnSeconds = game.settings().getInt("golems.auto-respawn-seconds", 60);
        new GolemRespawnTask(this, golem.getTeam()).runTaskLater(plugin, respawnSeconds * 20L);
    }

    public void respawnIfShort(Team team) {
        if (!game.isActive()) return;
        int starting = game.settings().getInt("golems.starting", 2);
        if (game.getTeam(team).getGolems().size() < starting) spawnOne(team);
    }

    private void dropCarried(HeistGolem golem, Location location) {
        ItemStack carried = golem.getCarried();
        if (carried == null) return;
        LootItem.setLastTeam(carried, golem.getTeam());
        location.getWorld().dropItemNaturally(location, carried);
        golem.setCarried(null);
        updateLabel(golem);
    }

    // ---- oxidation ----

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

        int cooldown = (int) (game.settings().getInt("golems.scrape-cooldown-seconds", 20) * cooldownMultiplier);
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
        int duration = game.settings().getInt("golems.wax-duration-seconds", 180);
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

    /** Storm Rod: resets every own golem within range of the team's golem idle point to Fresh, and zaps every nearby player. */
    public void stormReset(Team team) {
        Arena.TeamSite site = game.getArena().site(team);
        if (site.golemIdle == null || site.golemIdle.getWorld() == null) return;
        Location center = site.golemIdle;
        double radius = game.settings().getDouble("golems.storm-rod.radius", 8.0);
        double radiusSquared = radius * radius;
        double zapDamage = game.settings().getDouble("golems.storm-rod.damage", 6.0);

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
        long base = game.settings().getLong("golems.aging-seconds", 210) * 1000L;
        long jitter = game.settings().getLong("golems.aging-jitter-seconds", 20) * 1000L;
        long jittered = base + (long) ((Math.random() * 2 - 1) * jitter);
        return Math.max(1000L, jittered);
    }

    // ---- shutdown ----

    public void despawnAll() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (HeistGolem golem : new ArrayList<>(golems.values())) {
            if (golem.getLabel() != null) golem.getLabel().remove();
            removeCosmeticPassengers(golem);
            golem.getEntity().remove();
            plugin.getGameManager().unregisterGolem(golem.getEntity().getUniqueId());
        }
        golems.clear();
        seenLoot.clear();
        holders.clear();
    }
}
