package me.psikuvit.copperHeist.game;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.arena.Region;
import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.event.PhaseChangeEvent;
import me.psikuvit.copperHeist.golem.GolemManager;
import me.psikuvit.copperHeist.heist.AlarmManager;
import me.psikuvit.copperHeist.heist.DockLockManager;
import me.psikuvit.copperHeist.heist.VaultDrillManager;
import me.psikuvit.copperHeist.loot.LootBagManager;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.loot.LootSpawner;
import me.psikuvit.copperHeist.npc.NpcHandle;
import me.psikuvit.copperHeist.npc.NpcLook;
import me.psikuvit.copperHeist.npc.NpcSpec;
import me.psikuvit.copperHeist.npc.VillagerNpcProvider;
import me.psikuvit.copperHeist.relic.RelicManager;
import me.psikuvit.copperHeist.role.RoleService;
import me.psikuvit.copperHeist.task.GameSidebarTask;
import me.psikuvit.copperHeist.task.GameTimerTask;
import me.psikuvit.copperHeist.task.GameUiTask;
import me.psikuvit.copperHeist.task.OxidationTask;
import me.psikuvit.copperHeist.task.RejoinExpiryTask;
import me.psikuvit.copperHeist.task.RespawnTask;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import me.psikuvit.copperHeist.util.PlayerSanitizer;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Game {

    private final CopperHeist plugin;
    private final Arena arena;
    private final Settings settings;
    private final Map<Team, GameTeam> teams = new EnumMap<>(Team.class);
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final Map<UUID, GamePlayer.SavedState> spectators = new LinkedHashMap<>();
    private boolean lootStarted;
    private final Map<UUID, Team> shopNpcs = new LinkedHashMap<>();
    private final List<Entity> npcEntities = new ArrayList<>();
    private final Map<UUID, Long> disconnectedUntil = new LinkedHashMap<>();
    private final Set<String> scoredLootIds = new HashSet<>();

    private GameState state = GameState.WAITING;
    private String matchId = UUID.randomUUID().toString();
    private int secondsRemaining = 0;
    private int matchDurationSeconds = 0;
    private BossBar phaseBar;
    private int forfeitCountdown = -1;
    private Team forfeitWinner;

    private final GolemManager golemManager;
    private final LootSpawner lootSpawner;
    private final RoleService roleService;
    private final RelicManager relicManager;
    private final AlarmManager alarmManager;
    private final VaultDrillManager vaultDrillManager;
    private final LootBagManager lootBagManager;
    private final DockLockManager dockLocks;

    private final BukkitTask timerTask;
    private final BukkitTask sidebarTask;
    private BukkitTask oxidationTask;
    private BukkitTask uiTask;
    private int uiTicks;

    public Game(CopperHeist plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.settings = plugin.settings()
                .withOverride(() -> plugin.getPresets().get(arena.getPreset()))
                .withOverride(arena::getOverrides);
        teams.put(Team.COPPER, new GameTeam(Team.COPPER));
        teams.put(Team.IRON, new GameTeam(Team.IRON));
        this.golemManager = new GolemManager(plugin, this);
        this.lootSpawner = new LootSpawner(this);
        this.roleService = new RoleService(plugin, this);
        this.relicManager = new RelicManager(plugin, this);
        this.alarmManager = new AlarmManager(plugin, this);
        this.vaultDrillManager = new VaultDrillManager(plugin, this);
        this.lootBagManager = new LootBagManager(plugin, this);
        this.dockLocks = new DockLockManager(plugin, this);

        World world = arena.getWorld();
        if (world != null) world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);

        this.timerTask = new GameTimerTask(this).runTaskTimer(plugin, 20L, 20L);
        // Runs for the whole life of the Game (not just while a match is active) so WAITING/STARTING
        // players see a lobby board and ENDING/RESETTING still shows the result.
        this.sidebarTask = new GameSidebarTask(plugin.getSidebarService(), this).runTaskTimer(plugin, 20L, 20L);
    }

    public CopperHeist getPlugin() {
        return plugin;
    }

    /** config.yml with this arena's preset and overrides layered on top - use this, not the plugin's, for anything match-related. */
    public Settings settings() {
        return settings;
    }

    public Arena getArena() {
        return arena;
    }

    public GameState getState() {
        return state;
    }

    public String getMatchId() {
        return matchId;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public GameTeam getTeam(Team team) {
        return teams.get(team);
    }

    public GamePlayer getGamePlayer(UUID uuid) {
        return players.get(uuid);
    }

    public GolemManager getGolemManager() {
        return golemManager;
    }

    public RoleService getRoleService() {
        return roleService;
    }

    public RelicManager getRelicManager() {
        return relicManager;
    }

    public AlarmManager getAlarmManager() {
        return alarmManager;
    }

    public VaultDrillManager getVaultDrillManager() {
        return vaultDrillManager;
    }

    public boolean isPlaying(GamePlayer gp) {
        return players.get(gp.getUuid()) == gp;
    }

    public DockLockManager getDockLocks() {
        return dockLocks;
    }

    public LootBagManager getLootBagManager() {
        return lootBagManager;
    }

    /** features.<name> in config.yml - lets an owner switch whole mechanics off (default: on). */
    public boolean feature(String name) {
        return settings().getBoolean("features." + name, true);
    }

    /** Final Rush's bonuses (double loot, faster aging, glow, faster respawns) apply only while the phase is on and the feature enabled. */
    public boolean isFinalRushActive() {
        return state == GameState.FINAL_RUSH && feature("final-rush");
    }

    public boolean isActive() {
        return state == GameState.SETUP || state == GameState.COLLECTION
                || state == GameState.HEIST || state == GameState.FINAL_RUSH;
    }

    /** Hidden-score mode: each team only sees its own score until Final Rush. */
    public boolean isScoreHidden() {
        return settings().getBoolean("match.hidden-enemy-score", false)
                && isActive() && state != GameState.FINAL_RUSH;
    }

    public boolean isHeistPhaseOrLater() {
        return isActive() && state.ordinal() >= GameState.HEIST.ordinal();
    }

    public List<Player> onlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : players.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) result.add(player);
        }
        return result;
    }

    public int totalPlayers() {
        return players.size();
    }

    /** Everyone in the match, including players who are offline right now - used to settle stats at match end. */
    public Collection<GamePlayer> gamePlayers() {
        return new ArrayList<>(players.values());
    }

    /** The player with the best MVP score, or null if nobody scored anything. */
    public GamePlayer mvp() {
        GamePlayer best = null;
        for (GamePlayer gp : players.values()) {
            if (gp.mvpScore() > 0 && (best == null || gp.mvpScore() > best.mvpScore())) best = gp;
        }
        return best;
    }

    public int elapsedSeconds() {
        return Math.max(0, matchDurationSeconds - secondsRemaining);
    }

    // ---- join/leave ----

    public boolean addPlayer(Player player) {
        int max = settings().getInt("match.max-players", 16);
        if (state != GameState.WAITING && state != GameState.STARTING) return false;
        if (players.size() >= max) return false;

        Team team = teams.get(Team.COPPER).getMembers().size() <= teams.get(Team.IRON).getMembers().size()
                ? Team.COPPER : Team.IRON;

        GamePlayer gamePlayer = new GamePlayer(player.getUniqueId(), team);
        gamePlayer.setRole(roleService.defaultRole(team));
        gamePlayer.setSavedState(new GamePlayer.SavedState(
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getLocation().clone(),
                player.getGameMode(),
                player.getHealth(),
                player.getFoodLevel()));

        players.put(player.getUniqueId(), gamePlayer);
        teams.get(team).getMembers().add(player.getUniqueId());

        PlayerSanitizer.reset(player);
        player.setGameMode(GameMode.SURVIVAL);
        if (arena.getLobby() != null) player.teleport(arena.getLobby());
        plugin.getLobbyKitService().giveLeaveItem(player);
        player.sendMessage(plugin.getMessageService().getWithPrefix(player, "join", "arena", arena.getName(), "team", team.displayName()));
        return true;
    }

    public void removePlayer(Player player, boolean online) {
        removePlayer(player, online, false);
    }

    /**
     * @param toHub true when the match is over: the player is sanitized and sent to the hub with the hub kit, instead of getting
     *              their pre-match inventory and location back (that is what leaving mid-match does)
     */
    public void removePlayer(Player player, boolean online, boolean toHub) {
        GamePlayer gamePlayer = players.remove(player.getUniqueId());
        if (gamePlayer == null) return;
        teams.get(gamePlayer.getTeam()).getMembers().remove(player.getUniqueId());

        plugin.getLootWeightService().clearModifier(player);
        if (isActive()) dropCarriedLoot(player);

        disconnectedUntil.remove(player.getUniqueId());
        if (online && toHub) sendToHub(player, gamePlayer.getSavedState());
        else if (online && gamePlayer.getSavedState() != null) restoreState(player, gamePlayer.getSavedState());
        else if (!online && gamePlayer.getSavedState() != null) plugin.getGameManager().stashRestore(player.getUniqueId(), gamePlayer.getSavedState());
        if (online) {
            if (phaseBar != null) player.hideBossBar(phaseBar);
            player.setGlowing(false);
            plugin.getSidebarService().clearMatchDecor(player);
            plugin.getSidebarService().showHub(player);
        }
    }

    /**
     * End of a match: everything the match gave the player is wiped (items, armor, offhand, effects, glow, speed, flight, vitals),
     * they go back to the hub spawn - or to where they were before joining if no hub is set - and get the hub kit. Their game
     * mode goes back to what it was before the match (never spectator) so nobody is stuck in adventure or spectator.
     */
    private void sendToHub(Player player, GamePlayer.SavedState saved) {
        PlayerSanitizer.reset(player);
        GameMode mode = saved == null || saved.gameMode() == GameMode.SPECTATOR ? Bukkit.getDefaultGameMode() : saved.gameMode();
        player.setGameMode(mode);
        Location hub = plugin.getHubSpawn().get();
        if (hub != null) player.teleport(hub);
        else if (saved != null) player.teleport(saved.location());
        plugin.getLobbyKitService().giveHubKit(player);
    }

    public static void restoreState(Player player, GamePlayer.SavedState saved) {
        // Whatever the match left on the player (items, potion effects, glow, speed) goes before their old state comes back.
        PlayerSanitizer.clearInventory(player);
        PlayerSanitizer.clearEffects(player);
        player.getInventory().setContents(saved.contents());
        player.getInventory().setArmorContents(saved.armor());
        player.setGameMode(saved.gameMode());
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(saved.health(), maxHealth == null ? 20.0 : maxHealth.getValue()));
        player.setFoodLevel(saved.foodLevel());
        player.teleport(saved.location());
    }

    // ---- shop NPCs ----

    /**
     * One NPC per shop point of each team. A shop point can name an npcs.yml look (a villager profession, a Mannequin skin, an armor stand
     * with a custom head and armor ...); without one the default shop look is used, and without that the plain npc.type from config.yml.
     */
    private void spawnShopNpcs() {
        String format = settings().getString("npc.name-format", "{team} Shop");
        var looks = plugin.getNpcLooks();
        for (Team team : Team.values()) {
            var skin = plugin.getCosmetics().bestForTeam(this, team, CosmeticCategory.NPC);
            for (Arena.ShopPoint point : arena.site(team).shops) {
                Location loc = point.location();
                if (loc == null || loc.getWorld() == null) continue;
                NpcLook look = looks.get(point.look());
                if (look == null) look = looks.defaultFor("shop");
                String type = look != null && look.type() != null ? look.type() : settings().getString("npc.type", "villager");
                String nameFormat = look != null && look.name() != null ? look.name() : format;
                Component name = Theme.mini().deserialize(nameFormat.replace("{team}", team.displayName())).colorIfAbsent(team.color());

                NpcSpec spec = new NpcSpec(loc, name, team, settings(), skin == null ? null : skin.cosmetic().params(), look);
                NpcHandle handle;
                try {
                    handle = plugin.providers().npc().resolve(type).spawn(spec);
                } catch (LinkageError | RuntimeException ex) {
                    plugin.getLogger().warning("NPC type '" + type + "' failed (" + ex + ") - falling back to a villager.");
                    handle = new VillagerNpcProvider().spawn(new NpcSpec(loc, name, team, settings()));
                }
                if (handle == null) continue;
                for (Entity entity : allNpcEntities(handle)) Pdc.set(entity, PdcKeys.MATCH_ID, matchId);
                shopNpcs.put(handle.clickable().getUniqueId(), team);
                npcEntities.addAll(allNpcEntities(handle));
                plugin.getGameManager().registerHeistEntity(this, handle.clickable().getUniqueId());
            }
        }
    }

    private List<Entity> allNpcEntities(NpcHandle handle) {
        List<Entity> all = new ArrayList<>();
        all.add(handle.clickable());
        all.addAll(handle.extras());
        return all;
    }

    private void removeShopNpcs() {
        for (UUID id : shopNpcs.keySet()) plugin.getGameManager().unregisterHeistEntity(id);
        for (Entity entity : npcEntities) entity.remove();
        npcEntities.clear();
        shopNpcs.clear();
    }

    public Team shopNpcTeam(UUID entityId) {
        return shopNpcs.get(entityId);
    }

    /**
     * Whether {@code loc} counts as inside the team's base: its base region when the arena defines one,
     * otherwise a circle of {@code fallbackRadius} blocks around the team spawn.
     */
    public boolean isInBase(Team team, Location loc, double fallbackRadius) {
        Arena.TeamSite site = arena.site(team);
        Region base = site.base();
        if (base != null) return base.contains(loc);
        Location spawn = site.spawn;
        if (spawn == null || loc.getWorld() == null || !loc.getWorld().equals(spawn.getWorld())) return false;
        return loc.distanceSquared(spawn) <= fallbackRadius * fallbackRadius;
    }

    /** "/ch shop" only works from your own base during a match. */
    public boolean isNearOwnSpawn(Player player, GamePlayer gp) {
        Arena.TeamSite site = arena.site(gp.getTeam());
        if (site.base() == null && site.spawn == null) return true;
        return isInBase(gp.getTeam(), player.getLocation(), settings().getDouble("shop.command-radius", 15));
    }

    /** Optional (match.setup-confine-to-base): during SETUP, anyone who wanders out of their base region is sent back to spawn. */
    private void confineToBase() {
        if (state != GameState.SETUP || !settings().getBoolean("match.setup-confine-to-base", false)) return;
        for (Player player : onlinePlayers()) {
            GamePlayer gp = players.get(player.getUniqueId());
            if (gp == null) continue;
            Arena.TeamSite site = arena.site(gp.getTeam());
            if (site.base() == null || site.spawn == null || site.base().contains(player.getLocation())) continue;
            player.teleport(site.spawn);
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "game.confined"));
        }
    }

    // ---- loot integrity ----

    /** False if this loot id was already scored this match - a duplicated item is worth nothing the second time. */
    public boolean markLootScored(String lootId) {
        return scoredLootIds.add(lootId);
    }

    /** Loot from a different match (an old bag, a stale inventory) can't be carried into this one. */
    private void purgeStaleLoot(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (LootItem.isLoot(item) && !matchId.equals(LootItem.getMatchId(item))) player.getInventory().setItem(i, null);
        }
    }

    // ---- disconnects ----

    /** Keeps a quitting player's slot open for a grace period: their loot drops now, but they can rejoin their team. */
    public boolean holdSlot(Player player) {
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp == null || !isActive()) return false;
        dropCarriedLoot(player);
        plugin.getLootWeightService().clearModifier(player);
        int grace = settings().getInt("match.rejoin-grace-seconds", 60);
        disconnectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + grace * 1000L);
        new RejoinExpiryTask(this, player.getUniqueId()).runTaskLater(plugin, grace * 20L);
        return true;
    }

    public boolean rejoin(Player player) {
        if (disconnectedUntil.remove(player.getUniqueId()) == null) return false;
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp == null || !isActive()) return false;
        PlayerSanitizer.clearInventory(player);
        PlayerSanitizer.clearEffects(player);
        beginRespawnWait(player, gp);
        return true;
    }

    public void expireDisconnected(UUID uuid) {
        if (disconnectedUntil.containsKey(uuid)) removeOffline(uuid);
    }

    /** Removes a player who isn't online - their saved state is held until they next join. */
    public void removeOffline(UUID uuid) {
        disconnectedUntil.remove(uuid);
        GamePlayer gp = players.remove(uuid);
        if (gp == null) return;
        teams.get(gp.getTeam()).getMembers().remove(uuid);
        if (gp.getSavedState() != null) plugin.getGameManager().stashRestore(uuid, gp.getSavedState());
    }

    /** Plugin disable: end any running match and send everyone back where they came from. */
    public void shutdown() {
        if (state == GameState.RESETTING) return;
        if (isActive() || state == GameState.STARTING) end();
        beginReset();
    }

    // ---- spectators ----

    public boolean addSpectator(Player player) {
        Location where = arena.getSpectator() != null ? arena.getSpectator() : arena.getLobby();
        if (where == null) return false;
        spectators.put(player.getUniqueId(), new GamePlayer.SavedState(
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getLocation().clone(),
                player.getGameMode(),
                player.getHealth(),
                player.getFoodLevel()));
        PlayerSanitizer.clearInventory(player);
        PlayerSanitizer.clearEffects(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(where);
        return true;
    }

    public void removeSpectator(Player player, boolean online) {
        removeSpectator(player, online, false);
    }

    public void removeSpectator(Player player, boolean online, boolean toHub) {
        GamePlayer.SavedState saved = spectators.remove(player.getUniqueId());
        if (saved == null || !online) return;
        if (toHub) sendToHub(player, saved);
        else restoreState(player, saved);
        plugin.getSidebarService().clearMatchDecor(player);
        plugin.getSidebarService().showHub(player);
    }

    public boolean isSpectator(Player player) {
        return spectators.containsKey(player.getUniqueId());
    }

    public List<Player> spectatorPlayers() {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : spectators.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) result.add(player);
        }
        return result;
    }

    // ---- debug controls ----

    /** Jumps the clock to the start of a phase (the next tick performs the transition); returns false if the match isn't running or the phase isn't one of the four. */
    public boolean forcePhase(GameState target) {
        if (!isActive()) return false;
        int elapsed = switch (target) {
            case SETUP -> 0;
            case COLLECTION -> settings().getInt("match.phases.setup-seconds", 60);
            case HEIST -> settings().getInt("match.phases.collection-end-seconds", 480);
            case FINAL_RUSH -> settings().getInt("match.phases.heist-end-seconds", 780);
            default -> -1;
        };
        if (elapsed < 0) return false;
        secondsRemaining = matchDurationSeconds - elapsed;
        return true;
    }

    // ---- state machine ----

    public void tick() {
        switch (state) {
            case WAITING -> tickWaiting();
            case STARTING -> tickStarting();
            case SETUP, COLLECTION, HEIST, FINAL_RUSH -> tickRunning();
            case ENDING -> tickEnding();
            case RESETTING -> {
            }
        }
    }

    private void tickWaiting() {
        int min = settings().getInt("match.min-players", 6);
        if (players.size() >= min) {
            state = GameState.STARTING;
            secondsRemaining = settings().getInt("match.starting-countdown-seconds", 10);
            broadcast("game.starting-soon", "seconds", secondsRemaining);
        }
    }

    private void tickStarting() {
        int min = settings().getInt("match.min-players", 6);
        if (players.size() < min) {
            state = GameState.WAITING;
            broadcast("game.countdown-cancelled");
            return;
        }
        secondsRemaining--;
        if (secondsRemaining <= 0) {
            start();
        } else if (secondsRemaining <= 5 || secondsRemaining % 10 == 0) {
            broadcast("game.countdown", "seconds", secondsRemaining);
        }
    }

    private void tickRunning() {
        secondsRemaining--;
        if (secondsRemaining <= 0) {
            end();
            return;
        }
        if (checkForfeit()) return;
        GameState target = phaseFor(matchDurationSeconds - secondsRemaining);
        if (target != state) transitionPhase(target);
        updatePhaseBar();
    }

    /** One team empty starts a countdown; if it runs out the remaining team wins regardless of score. */
    private boolean checkForfeit() {
        Team remaining = null;
        int nonEmpty = 0;
        for (Team team : Team.values()) {
            if (!teams.get(team).getMembers().isEmpty()) {
                nonEmpty++;
                remaining = team;
            }
        }
        if (nonEmpty >= 2) {
            forfeitCountdown = -1;
            return false;
        }
        if (nonEmpty == 0) {
            end();
            return true;
        }
        if (forfeitCountdown < 0) {
            forfeitCountdown = settings().getInt("match.forfeit-seconds", 30);
            broadcast("game.forfeit-warning", "team", remaining.displayName(), "seconds", forfeitCountdown);
            return false;
        }
        if (--forfeitCountdown <= 0) {
            forfeitWinner = remaining;
            end();
            return true;
        }
        return false;
    }

    /** Puts a freshly killed player in spectator for the respawn delay, then sends them back to spawn with their loadout. */
    public void beginRespawnWait(Player player, GamePlayer gp) {
        plugin.providers().respawn().resolve(settings().getString("respawn.mode", "spectator-wait")).begin(this, player, gp);
    }

    public void finishRespawn(Player player, GamePlayer gp) {
        gp.setGhost(false);
        Location spawn = arena.site(gp.getTeam()).spawn;
        if (spawn != null) player.teleport(spawn);
        roleService.giveLoadout(player, gp.getRole(), gp.getTeam());
        gp.protectFor(settings().getInt("spawn-protection.invulnerable-seconds", 3));
    }

    private GameState phaseFor(int elapsed) {
        int setupEnd = settings().getInt("match.phases.setup-seconds", 60);
        int collectionEnd = settings().getInt("match.phases.collection-end-seconds", 480);
        int heistEnd = settings().getInt("match.phases.heist-end-seconds", 780);
        if (elapsed < setupEnd) return GameState.SETUP;
        if (elapsed < collectionEnd) return GameState.COLLECTION;
        if (elapsed < heistEnd) return GameState.HEIST;
        return GameState.FINAL_RUSH;
    }

    private void transitionPhase(GameState target) {
        GameState from = state;
        state = target;

        if (target != GameState.SETUP && !lootStarted) {
            lootStarted = true;
            lootSpawner.start();
            for (GameTeam gameTeam : teams.values()) gameTeam.markDelivery();
        }
        if (target == GameState.FINAL_RUSH && feature("final-rush") && settings().getBoolean("final-rush.all-players-glow", true)) {
            for (Player player : onlinePlayers()) player.setGlowing(true);
        }

        Bukkit.getPluginManager().callEvent(new PhaseChangeEvent(this, from, target));
    }

    private void updatePhaseBar() {
        if (phaseBar == null) return;
        phaseBar.name(plugin.getMessageService().get("phase.bar",
                "phase", state.name().replace('_', ' '), "time", formatTime(secondsRemaining)));
        phaseBar.progress(Math.clamp(secondsRemaining / (float) matchDurationSeconds, 0f, 1f));
        phaseBar.color(switch (state) {
            case SETUP -> BossBar.Color.BLUE;
            case COLLECTION -> BossBar.Color.GREEN;
            case HEIST -> BossBar.Color.YELLOW;
            default -> BossBar.Color.RED;
        });
    }

    private static String formatTime(int seconds) {
        return String.format("%02d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }

    private void tickEnding() {
        secondsRemaining--;
        if (secondsRemaining <= 0) beginReset();
    }

    public void forceStart() {
        if (state == GameState.WAITING || state == GameState.STARTING) start();
    }

    public void forceStop() {
        if (isActive() || state == GameState.STARTING) end();
    }

    private void start() {
        state = GameState.SETUP;
        matchId = UUID.randomUUID().toString();
        matchDurationSeconds = settings().getInt("match.duration-seconds", 900);
        lootStarted = false;
        secondsRemaining = matchDurationSeconds;

        for (Team team : Team.values()) {
            GameTeam gameTeam = teams.get(team);
            Arena.TeamSite site = arena.site(team);
            for (UUID uuid : gameTeam.getMembers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                if (site.spawn != null) player.teleport(site.spawn);
                GamePlayer gp = players.get(uuid);
                roleService.giveLoadout(player, gp.getRole(), team);
                gp.protectFor(settings().getInt("spawn-protection.invulnerable-seconds", 3));
            }
            golemManager.spawnStarting(team);
        }

        if (feature("relics")) relicManager.start();
        if (feature("alarms")) alarmManager.start();
        if (feature("vault-drill")) vaultDrillManager.start();
        lootBagManager.start();
        spawnShopNpcs();
        oxidationTask = new OxidationTask(this).runTaskTimer(plugin, 20L, 20L);
        uiTask = new GameUiTask(this).runTaskTimer(plugin, 10L, 10L);

        phaseBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        updatePhaseBar();
        for (Player player : onlinePlayers()) player.showBossBar(phaseBar);

        for (Player player : onlinePlayers()) {
            player.showTitle(Title.title(
                    plugin.getMessageService().get(player, "game.title"),
                    plugin.getMessageService().get(player, "game.subtitle"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
    }

    /** A player leaving mid-match doesn't take their loot with them - it drops where they stood (the relic respawns instead). */
    private void dropCarriedLoot(Player player) {
        boolean lostRelic = false;
        List<ItemStack> dropped = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !LootItem.isLoot(item)) continue;
            if (LootItem.isRelic(item)) lostRelic = true;
            else dropped.add(item);
        }
        lootBagManager.create(player.getLocation(), dropped);
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (LootItem.isLoot(player.getInventory().getItem(i))) player.getInventory().setItem(i, null);
        }
        if (lostRelic) relicManager.onLost(player);
    }

    private void spawnGuardTick() {
        if (state == GameState.SETUP) return;
        double radius = settings().getDouble("spawn-protection.radius", 6);
        double damage = settings().getDouble("spawn-protection.damage-per-second", 2.0);
        for (Team team : Team.values()) {
            Location spawn = arena.site(team).spawn;
            if (spawn == null || spawn.getWorld() == null) continue;
            for (UUID uuid : teams.get(team.opposite()).getMembers()) {
                Player enemy = Bukkit.getPlayer(uuid);
                if (enemy == null || !enemy.getWorld().equals(spawn.getWorld())) continue;
                if (enemy.getLocation().distanceSquared(spawn) <= radius * radius) enemy.damage(damage);
            }
        }
    }

    public void uiTick() {
        if (++uiTicks % 2 == 0) {
            spawnGuardTick();
            confineToBase();
        }
        golemManager.syncLabels();
        for (Player player : onlinePlayers()) {
            purgeStaleLoot(player);
            plugin.getLootWeightService().recalc(player);
        }
    }

    private void end() {
        if (state == GameState.ENDING || state == GameState.RESETTING) return;
        state = GameState.ENDING;
        secondsRemaining = settings().getInt("match.ending-seconds", 10);
        lootSpawner.stop();
        relicManager.stop();
        alarmManager.stop();
        vaultDrillManager.stop();
        lootBagManager.stop();
        removeShopNpcs();
        if (oxidationTask != null) oxidationTask.cancel();
        if (uiTask != null) uiTask.cancel();
        if (phaseBar != null) {
            for (Player player : onlinePlayers()) {
                player.hideBossBar(phaseBar);
                player.setGlowing(false);
            }
            phaseBar = null;
        }

        GameTeam copper = teams.get(Team.COPPER);
        GameTeam iron = teams.get(Team.IRON);
        Team winner = getTeam(copper, iron);
        Bukkit.getPluginManager().callEvent(new MatchEndEvent(this, winner, copper.getScore(), iron.getScore()));
    }

    private @Nullable Team getTeam(GameTeam copper, GameTeam iron) {
        Team winner = null;
        if (copper.getScore() != iron.getScore()) {
            winner = copper.getScore() > iron.getScore() ? Team.COPPER : Team.IRON;
        } else if (copper.getRelicsDelivered() != iron.getRelicsDelivered()) {
            winner = copper.getRelicsDelivered() > iron.getRelicsDelivered() ? Team.COPPER : Team.IRON;
        } else if (copper.getSteals() != iron.getSteals()) {
            winner = copper.getSteals() > iron.getSteals() ? Team.COPPER : Team.IRON;
        }

        if (forfeitWinner != null) winner = forfeitWinner;
        return winner;
    }

    private void beginReset() {
        state = GameState.RESETTING;
        golemManager.despawnAll();
        plugin.providers().reset().resolve(settings().getString("reset.method", "entities")).reset(arena);

        for (UUID uuid : new ArrayList<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removePlayer(player, true, true);
            else removeOffline(uuid);
        }

        for (UUID uuid : new ArrayList<>(spectators.keySet())) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) removeSpectator(spectator, true, true);
            else spectators.remove(uuid);
        }

        if (timerTask != null) timerTask.cancel();
        if (sidebarTask != null) sidebarTask.cancel();
        plugin.getGameManager().onGameFinished(this);
    }

    private void broadcast(String key, Object... placeholders) {
        for (Player player : onlinePlayers()) player.sendMessage(plugin.getMessageService().get(player, key, placeholders));
    }
}
