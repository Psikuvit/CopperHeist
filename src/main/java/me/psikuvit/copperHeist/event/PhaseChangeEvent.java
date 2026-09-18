package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when a running match moves from one phase (SETUP, COLLECTION, HEIST, FINAL_RUSH) to the next. */
public class PhaseChangeEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameState from;
    private final GameState to;

    public PhaseChangeEvent(Game game, GameState from, GameState to) {
        super(game);
        this.from = from;
        this.to = to;
    }

    public GameState getFrom() {
        return from;
    }

    public GameState getTo() {
        return to;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
