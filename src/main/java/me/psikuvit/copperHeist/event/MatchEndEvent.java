package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.Team;
import org.bukkit.event.HandlerList;

/** Fired once, when a match's timer runs out or it's force-stopped. winner is null on a tie. */
public class MatchEndEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Team winner;
    private final int copperScore;
    private final int ironScore;

    public MatchEndEvent(Game game, Team winner, int copperScore, int ironScore) {
        super(game);
        this.winner = winner;
        this.copperScore = copperScore;
        this.ironScore = ironScore;
    }

    public Team getWinner() {
        return winner;
    }

    public int getCopperScore() {
        return copperScore;
    }

    public int getIronScore() {
        return ironScore;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
