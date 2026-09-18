package me.psikuvit.copperHeist.game;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.golem.GolemManager;
import me.psikuvit.copperHeist.golem.OxidationTask;
import me.psikuvit.copperHeist.loot.LootSpawner;
import me.psikuvit.copperHeist.relic.RelicManager;
import me.psikuvit.copperHeist.role.RoleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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

    private GameState state = GameState.WAITING;
    private String matchId = UUID.randomUUID().toString();
    private int secondsRemaining = 0;

    private final GolemManager golemManager;
    private final LootSpawner lootSpawner;
    private final RoleService roleService;
    private final RelicManager relicManager;

    private final BukkitTask timerTask;
    private final BukkitTask sidebarTask;
    private BukkitTask oxidationTask;
    private BukkitTask uiTask;

    public Game(CopperHeist plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        teams.put(Team.COPPER, new GameTeam(Team.COPPER));
        teams.put(Team.IRON, new GameTeam(Team.IRON));
        this.golemManager = new GolemManager(plugin, this);
        this.lootSpawner = new LootSpawner(this);
        this.roleService = new RoleService(plugin, this);
        this.relicManager = new RelicManager(plugin, this);

        World world = arena.getWorld();
        if (world != null) world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);

        this.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // Runs for the whole life of the Game (not just RUNNING) so WAITING/STARTING
        // players see a lobby board and ENDING/RESETTING still shows the result.
        this.sidebarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.getSidebarService().update(this), 20L, 20L);
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

    public boolean isActive() {
        return state == GameState.RUNNING;
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

        if (online && gamePlayer.getSavedState() != null) {
            GamePlayer.SavedState saved = gamePlayer.getSavedState();
            player.getInventory().setContents(saved.contents());
            player.getInventory().setArmorContents(saved.armor());
            player.setGameMode(saved.gameMode());
            player.setHealth(Math.min(saved.health(), player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            player.setFoodLevel(saved.foodLevel());
            player.teleport(saved.location());
        }
        if (online) {
            plugin.getSidebarService().showHub(player);
        }

        if (state == GameState.RUNNING) {
            Team remaining = null;
            for (Team team : Team.values()) {
                if (!teams.get(team).getMembers().isEmpty()) {
                    if (remaining != null) return; // both teams still have players
                    remaining = team;
                }
            }
            if (remaining != null) {
                broadcast(Component.text("Other team left - " + remaining.displayName() + " wins by forfeit!", NamedTextColor.YELLOW));
                end();
            }
        }
    }

    // ---- state machine ----

    private void tick() {
        switch (state) {
            case WAITING -> tickWaiting();
            case STARTING -> tickStarting();
            case RUNNING -> tickRunning();
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
        if (secondsRemaining <= 0) end();
    }

    private void tickEnding() {
        secondsRemaining--;
        if (secondsRemaining <= 0) beginReset();
    }

    public void forceStart() {
        if (state == GameState.WAITING || state == GameState.STARTING) start();
    }

    public void forceStop() {
        if (state == GameState.RUNNING || state == GameState.STARTING) end();
    }

    private void start() {
        state = GameState.RUNNING;
        matchId = UUID.randomUUID().toString();
        secondsRemaining = plugin.getConfig().getInt("match.duration-seconds", 900);

        for (Team team : Team.values()) {
            GameTeam gameTeam = teams.get(team);
            Arena.TeamSite site = arena.site(team);
            for (UUID uuid : gameTeam.getMembers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                if (site.spawn != null) player.teleport(site.spawn);
                GamePlayer gp = players.get(uuid);
                roleService.giveLoadout(player, gp.getRole(), team);
            }
            golemManager.spawnStarting(team);
        }

        lootSpawner.start();
        relicManager.start();
        oxidationTask = new OxidationTask(this).runTaskTimer(plugin, 20L, 20L);
        uiTask = Bukkit.getScheduler().runTaskTimer(plugin, this::uiTick, 10L, 10L);

        for (Player player : onlinePlayers()) {
            player.showTitle(Title.title(
                    Component.text("COPPER HEIST", NamedTextColor.GOLD),
                    Component.text("Fill your vault!", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
    }

    private void uiTick() {
        golemManager.syncLabels();
        for (Player player : onlinePlayers()) {
            plugin.getLootWeightService().recalc(player);
        }
    }

    public void onLootDelivered(Team team, int value) {
        broadcast(Component.text(team.displayName() + " delivered " + value + " loot! (" + teams.get(team).getScore() + " total)", team.color()));
    }

    public void onRelicDelivered(Team team) {
        broadcast(plugin.getMessageService().get("relic.delivered", "team", team.displayName()));
    }

    private void end() {
        if (state == GameState.ENDING || state == GameState.RESETTING) return;
        state = GameState.ENDING;
        secondsRemaining = plugin.getConfig().getInt("match.ending-seconds", 10);
        lootSpawner.stop();
        relicManager.stop();
        if (oxidationTask != null) oxidationTask.cancel();
        if (uiTask != null) uiTask.cancel();

        GameTeam copper = teams.get(Team.COPPER);
        GameTeam iron = teams.get(Team.IRON);
        Team winner = null;
        if (copper.getScore() != iron.getScore()) {
            winner = copper.getScore() > iron.getScore() ? Team.COPPER : Team.IRON;
        } else if (copper.getSteals() != iron.getSteals()) {
            winner = copper.getSteals() > iron.getSteals() ? Team.COPPER : Team.IRON;
        }

        Component summary = winner != null
                ? Component.text("WINNER: " + winner.displayName().toUpperCase() + " TEAM (" + copper.getScore() + " - " + iron.getScore() + ")", winner.color())
                : Component.text("DRAW (" + copper.getScore() + " - " + iron.getScore() + ")", NamedTextColor.YELLOW);
        broadcast(summary);
        for (Player player : onlinePlayers()) {
            player.showTitle(Title.title(
                    winner != null ? Component.text(winner.displayName() + " WINS", winner.color()) : Component.text("DRAW", NamedTextColor.YELLOW),
                    Component.text(copper.getScore() + " - " + iron.getScore(), NamedTextColor.GRAY)));
        }
    }

    private void beginReset() {
        state = GameState.RESETTING;
        golemManager.despawnAll();
        plugin.getArenaResetter().reset(arena);

        for (UUID uuid : new ArrayList<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removePlayer(player, true);
            else players.remove(uuid);
        }

        if (timerTask != null) timerTask.cancel();
        if (sidebarTask != null) sidebarTask.cancel();
        plugin.getGameManager().onGameFinished(this);
    }

    private void broadcast(Component message) {
        for (Player player : onlinePlayers()) player.sendMessage(message);
    }
}
