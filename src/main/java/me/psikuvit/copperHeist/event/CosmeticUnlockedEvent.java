package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired after a player gained a cosmetic, whether they bought it, were given it or earned it. */
public class CosmeticUnlockedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CosmeticDefinition cosmetic;
    private final String reason;

    public CosmeticUnlockedEvent(Player player, CosmeticDefinition cosmetic, String reason) {
        this.player = player;
        this.cosmetic = cosmetic;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public CosmeticDefinition getCosmetic() {
        return cosmetic;
    }

    /** "purchase", "grant" (admin or another plugin) ... */
    public String getReason() {
        return reason;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
