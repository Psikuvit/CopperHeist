package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.heist.VaultDrill;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when a Vault Drill finishes and its target vault opens for looting. */
public class VaultDrillCompletedEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final VaultDrill drill;
    private final int breachSeconds;

    public VaultDrillCompletedEvent(Game game, VaultDrill drill, int breachSeconds) {
        super(game);
        this.drill = drill;
        this.breachSeconds = breachSeconds;
    }

    public VaultDrill getDrill() {
        return drill;
    }

    public int getBreachSeconds() {
        return breachSeconds;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
