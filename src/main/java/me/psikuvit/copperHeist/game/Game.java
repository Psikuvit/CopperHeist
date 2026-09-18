package me.psikuvit.copperHeist.game;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.event.PhaseChangeEvent;
import me.psikuvit.copperHeist.golem.GolemManager;
import me.psikuvit.copperHeist.task.OxidationTask;
import me.psikuvit.copperHeist.heist.AlarmManager;
import me.psikuvit.copperHeist.heist.DockLockManager;
import me.psikuvit.copperHeist.heist.VaultDrillManager;
import me.psikuvit.copperHeist.loot.LootBagManager;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.loot.LootSpawner;
import me.psikuvit.copperHeist.relic.RelicManager;
import me.psikuvit.copperHeist.role.RoleService;
import me.psikuvit.copperHeist.task.GameSidebarTask;
import me.psikuvit.copperHeist.task.GameTimerTask;
import me.psikuvit.copperHeist.task.GameUiTask;
import me.psikuvit.copperHeist.task.RejoinExpiryTask;
import me.psikuvit.copperHeist.task.RespawnTask;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Game {

    private final CopperHeist plugin;
    private final Arena arena;
    private final Map<Team, GameTeam> teams = new EnumMap<>(Team.class);
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final Map<UUID, GamePlayer.SavedState> spectators = new LinkedHashMap<>();
    private boolean lootStarted;
    private final Map<UUID, Team> shopNpcs = new LinkedHashMap<>();
    private final Map<UUID, Long> disconnectedUntil = new LinkedHashMap<>();
    private final java.util.Set<String> scoredLootIds = new java.util.HashSet<>();

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

    public boolean isActive() {
        return state == GameState.SETUP || state == GameState.COLLECTION
                || state == GameState.HEIST || state == GameState.FINAL_RUSH;
    }

    /** Hidden-score mode: each team only sees its own score until Final Rush. */
    public boolean isScoreHidden() {
        return plugin.getConfig().getBoolean("match.hidden-enemy-score", false)
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

    // ---- join/leave ----

    public boolean addPlayer(Player player) {
        int max = plugin.getConfig().getInt("match.max-players", 16);
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

        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        if (arena.getLobby() != null) player.teleport(arena.getLobby());
        plugin.getLobbyKitService().giveLeaveItem(player);
        player.sendMessage(plugin.getMessageService().getWithPrefix("join", "arena", arena.getName(), "team", team.displayName()));
        return true;
    }

    public void removePlayer(Player player, boolean online) {
        GamePlayer gamePlayer = players.remove(player.getUniqueId());
        if (gamePlayer == null) return;
        teams.get(gamePlayer.getTeam()).getMembers().remove(player.getUniqueId());

        plugin.getLootWeightService().clearModifier(player);
        if (isActive()) dropCarriedLoot(player);

        disconnectedUntil.remove(player.getUniqueId());
        if (online && gamePlayer.getSavedState() != null) restoreState(player, gamePlayer.getSavedState());
        else if (!online && gamePlayer.getSavedState() != null) plugin.getGameManager().stashRestore(player.getUniqueId(), gamePlayer.getSavedState());
        if (online) {
            if (phaseBar != null) player.hideBossBar(phaseBar);
            player.setGlowing(false);
            plugin.getSidebarService().clearMatchDecor(player);
            plugin.getSidebarService().showHub(player);
        }
    }

    public static void restoreState(Player player, GamePlayer.SavedState saved) {
        player.getInventory().setContents(saved.contents());
        player.getInventory().setArmorContents(saved.armor());
        player.setGameMode(saved.gameMode());
        var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(Math.min(saved.health(), maxHealth == null ? 20.0 : maxHealth.getValue()));
        player.setFoodLevel(saved.foodLevel());
        player.teleport(saved.location());
    }

    // ---- shop NPCs ----

    private void spawnShopNpcs() {
        for (Team team : Team.values()) {
            Location loc = arena.site(team).shop;
            if (loc == null || loc.getWorld() == null) continue;
            Villager npc = loc.getWorld().spawn(loc, Villager.class, villager -> {
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setPersistent(true);
                villager.customName(Component.text(team.displayName() + " Shop", team.color()));
                villager.setCustomNameVisible(true);
            });
            Pdc.set(npc, PdcKeys.MATCH_ID, matchId);
            shopNpcs.put(npc.getUniqueId(), team);
            plugin.getGameManager().registerHeistEntity(this, npc.getUniqueId());
        }
    }

    private void removeShopNpcs() {
        for (UUID id : shopNpcs.keySet()) {
            var entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
            plugin.getGameManager().unregisterHeistEntity(id);
        }
        shopNpcs.clear();
    }

    public Team shopNpcTeam(UUID entityId) {
        return shopNpcs.get(entityId);
    }

    /** "/ch shop" only works from your own base during a match - approximated as a radius around your team's spawn. */
    public boolean isNearOwnSpawn(Player player, GamePlayer gp) {
        Location spawn = arena.site(gp.getTeam()).spawn;
        if (spawn == null || !player.getWorld().equals(spawn.getWorld())) return true;
        double radius = plugin.getConfig().getDouble("shop.command-radius", 15);
        return player.getLocation().distanceSquared(spawn) <= radius * radius;
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
        int grace = plugin.getConfig().getInt("match.rejoin-grace-seconds", 60);
        disconnectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + grace * 1000L);
        new RejoinExpiryTask(this, player.getUniqueId()).runTaskLater(plugin, grace * 20L);
        return true;
    }

    public boolean rejoin(Player player) {
        if (disconnectedUntil.remove(player.getUniqueId()) == null) return false;
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp == null || !isActive()) return false;
        player.getInventory().clear();
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
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(where);
        return true;
    }

    public void removeSpectator(Player player, boolean online) {
        GamePlayer.SavedState saved = spectators.remove(player.getUniqueId());
        if (saved == null || !online) return;
        restoreState(player, saved);
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
            case COLLECTION -> plugin.getConfig().getInt("match.phases.setup-seconds", 60);
            case HEIST -> plugin.getConfig().getInt("match.phases.collection-end-seconds", 480);
            case FINAL_RUSH -> plugin.getConfig().getInt("match.phases.heist-end-seconds", 780);
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
        int min = plugin.getConfig().getInt("match.min-players", 6);
        if (players.size() >= min) {
            state = GameState.STARTING;
            secondsRemaining = plugin.getConfig().getInt("match.starting-countdown-seconds", 10);
            broadcast(Component.text("Enough players - starting in " + secondsRemaining + "s", NamedTextColor.GREEN));
        }
    }

    private void tickStarting() {
        int min = plugin.getConfig().getInt("match.min-players", 6);
        if (players.size() < min) {
            state = GameState.WAITING;
            broadcast(Component.text("Not enough players - countdown cancelled", NamedTextColor.RED));
            return;
        }
        secondsRemaining--;
        if (secondsRemaining <= 0) {
            start();
        } else if (secondsRemaining <= 5 || secondsRemaining % 10 == 0) {
            broadcast(Component.text("Starting in " + secondsRemaining + "...", NamedTextColor.YELLOW));
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
            forfeitCountdown = plugin.getConfig().getInt("match.forfeit-seconds", 30);
            broadcast(Component.text("Other team left - " + remaining.displayName() + " wins by forfeit in "
                    + forfeitCountdown + "s.", NamedTextColor.YELLOW));
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
        int delay = plugin.getConfig().getInt("match.respawn-delay-seconds", 6);
        if (delay <= 0) {
            finishRespawn(player, gp);
            return;
        }
        player.setGameMode(GameMode.SPECTATOR);
        new RespawnTask(plugin, this, player, gp, delay).runTaskTimer(plugin, 0L, 20L);
    }

    public void finishRespawn(Player player, GamePlayer gp) {
        Location spawn = arena.site(gp.getTeam()).spawn;
        if (spawn != null) player.teleport(spawn);
        roleService.giveLoadout(player, gp.getRole(), gp.getTeam());
        gp.protectFor(plugin.getConfig().getInt("spawn-protection.invulnerable-seconds", 3));
    }

    private GameState phaseFor(int elapsed) {
        int setupEnd = plugin.getConfig().getInt("match.phases.setup-seconds", 60);
        int collectionEnd = plugin.getConfig().getInt("match.phases.collection-end-seconds", 480);
        int heistEnd = plugin.getConfig().getInt("match.phases.heist-end-seconds", 780);
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
        if (target == GameState.FINAL_RUSH && plugin.getConfig().getBoolean("final-rush.all-players-glow", true)) {
            for (Player player : onlinePlayers()) player.setGlowing(true);
        }

        Bukkit.getPluginManager().callEvent(new PhaseChangeEvent(this, from, target));
    }

    private void updatePhaseBar() {
        if (phaseBar == null) return;
        phaseBar.name(plugin.getMessageService().get("phase.bar",
                "phase", state.name().replace('_', ' '), "time", formatTime(secondsRemaining)));
        phaseBar.progress(Math.max(0f, Math.min(1f, secondsRemaining / (float) matchDurationSeconds)));
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
        matchDurationSeconds = plugin.getConfig().getInt("match.duration-seconds", 900);
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
                gp.protectFor(plugin.getConfig().getInt("spawn-protection.invulnerable-seconds", 3));
            }
            golemManager.spawnStarting(team);
        }

        relicManager.start();
        alarmManager.start();
        vaultDrillManager.start();
        lootBagManager.start();
        spawnShopNpcs();
        oxidationTask = new OxidationTask(this).runTaskTimer(plugin, 20L, 20L);
        uiTask = new GameUiTask(this).runTaskTimer(plugin, 10L, 10L);

        phaseBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        updatePhaseBar();
        for (Player player : onlinePlayers()) player.showBossBar(phaseBar);

        for (Player player : onlinePlayers()) {
            player.showTitle(Title.title(
                    Component.text("COPPER HEIST", NamedTextColor.GOLD),
                    Component.text("Fill your vault!", NamedTextColor.GRAY),
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
        double radius = plugin.getConfig().getDouble("spawn-protection.radius", 6);
        double damage = plugin.getConfig().getDouble("spawn-protection.damage-per-second", 2.0);
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
        if (++uiTicks % 2 == 0) spawnGuardTick();
        golemManager.syncLabels();
        for (Player player : onlinePlayers()) {
            purgeStaleLoot(player);
            plugin.getLootWeightService().recalc(player);
        }
    }

    private void end() {
        if (state == GameState.ENDING || state == GameState.RESETTING) return;
        state = GameState.ENDING;
        secondsRemaining = plugin.getConfig().getInt("match.ending-seconds", 10);
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
        Team winner = null;
        if (copper.getScore() != iron.getScore()) {
            winner = copper.getScore() > iron.getScore() ? Team.COPPER : Team.IRON;
        } else if (copper.getRelicsDelivered() != iron.getRelicsDelivered()) {
            winner = copper.getRelicsDelivered() > iron.getRelicsDelivered() ? Team.COPPER : Team.IRON;
        } else if (copper.getSteals() != iron.getSteals()) {
            winner = copper.getSteals() > iron.getSteals() ? Team.COPPER : Team.IRON;
        }

        if (forfeitWinner != null) winner = forfeitWinner;
        Bukkit.getPluginManager().callEvent(new MatchEndEvent(this, winner, copper.getScore(), iron.getScore()));
    }

    private void beginReset() {
        state = GameState.RESETTING;
        golemManager.despawnAll();
        plugin.getArenaResetter().reset(arena);

        for (UUID uuid : new ArrayList<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removePlayer(player, true);
            else removeOffline(uuid);
        }

        for (UUID uuid : new ArrayList<>(spectators.keySet())) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) removeSpectator(spectator, true);
            else spectators.remove(uuid);
        }

        if (timerTask != null) timerTask.cancel();
        if (sidebarTask != null) sidebarTask.cancel();
        plugin.getGameManager().onGameFinished(this);
    }

    private void broadcast(Component message) {
        for (Player player : onlinePlayers()) player.sendMessage(message);
    }
}
