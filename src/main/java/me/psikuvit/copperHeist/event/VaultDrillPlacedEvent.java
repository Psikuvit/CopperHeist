package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.heist.VaultDrill;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when a Vault Drill is placed on an enemy vault door. */
public class VaultDrillPlacedEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final VaultDrill drill;

    public VaultDrillPlacedEvent(Game game, VaultDrill drill) {
        super(game);
        this.drill = drill;
    }

    public VaultDrill getDrill() {
        return drill;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
