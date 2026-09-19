package me.psikuvit.copperHeist.api;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.game.Team;

import java.util.List;
import java.util.UUID;

/** A read-only snapshot-style view of one game. Calls read live values; nothing here changes the match. */
public final class GameView {

    private final Game game;

    public GameView(Game game) {
        this.game = game;
    }

    public String arenaName() {
        return game.getArena().getName();
    }

    public GameState state() {
        return game.getState();
    }

    public boolean isActive() {
        return game.isActive();
    }

    public String matchId() {
        return game.getMatchId();
    }

    public int secondsRemaining() {
        return game.getSecondsRemaining();
    }

    public int score(Team team) {
        return game.getTeam(team).getScore();
    }

    public int playerCount() {
        return game.totalPlayers();
    }

    /** The team the player is on in this game, or null if they aren't playing in it. */
    public Team teamOf(UUID player) {
        var gamePlayer = game.getGamePlayer(player);
        return gamePlayer == null ? null : gamePlayer.getTeam();
    }

    public List<UUID> players(Team team) {
        return List.copyOf(game.getTeam(team).getMembers());
    }
}
