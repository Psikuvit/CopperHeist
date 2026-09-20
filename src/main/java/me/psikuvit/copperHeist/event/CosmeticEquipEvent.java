package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired before a player equips a cosmetic; cancel it to refuse (for example a server that only allows some in certain worlds). */
public class CosmeticEquipEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CosmeticDefinition cosmetic;
    private boolean cancelled;

    public CosmeticEquipEvent(Player player, CosmeticDefinition cosmetic) {
        this.player = player;
        this.cosmetic = cosmetic;
    }

    public Player getPlayer() {
        return player;
    }

    public CosmeticDefinition getCosmetic() {
        return cosmetic;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
