package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when a player picks up the Relic off the ground. */
public class RelicPickupEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public RelicPickupEvent(Game game, Player player) {
        super(game);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
