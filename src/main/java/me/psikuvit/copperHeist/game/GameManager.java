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
                int max = plugin.getConfig().getInt("match.max-players", 16);
                if (game.totalPlayers() < max) return arena;
            }
        }
        return null;
    }

    public void leave(Player player) {
        Game game = getGame(player);
        if (game == null) return;
        game.removePlayer(player, true);
        playerArena.remove(player.getUniqueId());
    }

    public void onQuit(Player player) {
        Game game = getGame(player);
        if (game == null) return;
        game.removePlayer(player, false);
        playerArena.remove(player.getUniqueId());
    }

    public void onGameFinished(Game finishedGame) {
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
}
