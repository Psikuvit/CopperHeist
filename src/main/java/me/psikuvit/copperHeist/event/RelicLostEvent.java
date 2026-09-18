package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** Fired when the Relic's holder dies carrying it - it skips the usual ground-drop and respawns fresh instead. */
public class RelicLostEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player holder;

    public RelicLostEvent(Game game, Player holder) {
        super(game);
        this.holder = holder;
    }

    public Player getHolder() {
        return holder;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
