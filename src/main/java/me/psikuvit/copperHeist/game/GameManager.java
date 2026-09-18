package me.psikuvit.copperHeist.game;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.golem.HeistGolem;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final CopperHeist plugin;
    private final Map<String, Game> gamesByArena = new HashMap<>();
    private final Map<UUID, String> playerArena = new HashMap<>();
    private final Map<UUID, HeistGolem> golemRegistry = new HashMap<>();
    private final Map<UUID, Game> golemOwner = new HashMap<>();
    private final Map<UUID, Game> heistEntityOwner = new HashMap<>();
    private final Map<UUID, GamePlayer.SavedState> pendingRestore = new HashMap<>();
    private boolean shuttingDown;

    public GameManager(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public Collection<Game> all() {
        return gamesByArena.values();
    }

    public Game getGame(Arena arena) {
        return gamesByArena.computeIfAbsent(arena.getName().toLowerCase(), k -> new Game(plugin, arena));
    }

    /** Like {@link #getGame(Arena)} but never creates one - safe for read-only listing. */
    public Game peek(Arena arena) {
        return gamesByArena.get(arena.getName().toLowerCase());
    }

    public Game getGame(Player player) {
        String arenaName = playerArena.get(player.getUniqueId());
        return arenaName == null ? null : gamesByArena.get(arenaName);
    }

    public String join(Player player, String arenaName) {
        Arena arena = arenaName != null ? plugin.getArenaManager().get(arenaName) : findJoinableArena();
        if (arena == null) return "No joinable arena available right now.";
        if (!arena.isEnabled()) return "That arena isn't enabled.";

        Game game = getGame(arena);
        if (!game.addPlayer(player)) return "Couldn't join " + arena.getName() + " (full or already running).";
        playerArena.put(player.getUniqueId(), arena.getName().toLowerCase());
        return null;
    }

    private Arena findJoinableArena() {
        for (Arena arena : plugin.getArenaManager().all()) {
            if (!arena.isEnabled()) continue;
            Game game = getGame(arena);
            if (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING) {
                int max = plugin.settings().getInt("match.max-players", 16);
                if (game.totalPlayers() < max) return arena;
            }
        }
        return null;
    }

    public String spectate(Player player, String arenaName) {
        if (getGame(player) != null) return "Leave your current match first.";
        Arena arena = plugin.getArenaManager().get(arenaName);
        if (arena == null) return "No arena named '" + arenaName + "'.";
        Game game = peek(arena);
        if (game == null || (game.getState() == GameState.WAITING && game.totalPlayers() == 0)) {
            return "Nothing to spectate there right now.";
        }
        if (!game.addSpectator(player)) return "That arena has no spectator or lobby point set.";
        playerArena.put(player.getUniqueId(), arena.getName().toLowerCase());
        return null;
    }

    public void leave(Player player) {
        Game game = getGame(player);
        if (game == null) return;
        if (game.isSpectator(player)) game.removeSpectator(player, true);
        else game.removePlayer(player, true);
        playerArena.remove(player.getUniqueId());
    }

    /** Called on join: reconnects a player whose slot is still held, or restores the inventory/location they left a match with. */
    public boolean onJoin(Player player) {
        for (Game game : gamesByArena.values()) {
            if (game.rejoin(player)) {
                playerArena.put(player.getUniqueId(), game.getArena().getName().toLowerCase());
                return true;
            }
        }
        GamePlayer.SavedState saved = pendingRestore.remove(player.getUniqueId());
        if (saved != null) Game.restoreState(player, saved);
        return false;
    }

    /** Held until the player is next online - removing someone who has quit can't restore their state right then. */
    public void stashRestore(UUID uuid, GamePlayer.SavedState state) {
        pendingRestore.put(uuid, state);
    }

    public void shutdownAll() {
        shuttingDown = true;
        for (Game game : new java.util.ArrayList<>(gamesByArena.values())) game.shutdown();
    }

    public void onQuit(Player player) {
        Game game = getGame(player);
        if (game == null) return;
        if (game.isSpectator(player)) game.removeSpectator(player, false);
        else if (game.holdSlot(player)) return;
        else game.removePlayer(player, false);
        playerArena.remove(player.getUniqueId());
    }

    public void onGameFinished(Game finishedGame) {
        String arenaKey = finishedGame.getArena().getName().toLowerCase();
        playerArena.values().removeIf(arenaKey::equals);
        if (shuttingDown) return;
        gamesByArena.put(finishedGame.getArena().getName().toLowerCase(), new Game(plugin, finishedGame.getArena()));
    }

    public void registerGolem(Game owner, HeistGolem golem) {
        golemRegistry.put(golem.getEntity().getUniqueId(), golem);
        golemOwner.put(golem.getEntity().getUniqueId(), owner);
    }

    public void unregisterGolem(UUID entityId) {
        golemRegistry.remove(entityId);
        golemOwner.remove(entityId);
    }

    public HeistGolem getGolem(UUID entityId) {
        return golemRegistry.get(entityId);
    }

    public Game getGameForGolem(UUID entityId) {
        return golemOwner.get(entityId);
    }

    /** Shared registry for alarm/drill Interaction hitboxes - looked up by entity id, resolved further by the owning game's manager. */
    public void registerHeistEntity(Game owner, UUID entityId) {
        heistEntityOwner.put(entityId, owner);
    }

    public void unregisterHeistEntity(UUID entityId) {
        heistEntityOwner.remove(entityId);
    }

    public Game getGameForHeistEntity(UUID entityId) {
        return heistEntityOwner.get(entityId);
    }
}
