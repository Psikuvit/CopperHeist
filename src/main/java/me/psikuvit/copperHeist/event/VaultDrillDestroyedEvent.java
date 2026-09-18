package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.heist.VaultDrill;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when defenders break a Vault Drill before it finishes. */
public class VaultDrillDestroyedEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final VaultDrill drill;
    private final Player destroyer;

    public VaultDrillDestroyedEvent(Game game, VaultDrill drill, Player destroyer) {
        super(game);
        this.drill = drill;
        this.destroyer = destroyer;
    }

    public VaultDrill getDrill() {
        return drill;
    }

    public Player getDestroyer() {
        return destroyer;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
