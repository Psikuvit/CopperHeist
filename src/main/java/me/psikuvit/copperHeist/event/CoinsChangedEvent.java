package me.psikuvit.copperHeist.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Fired after a player's coin balance changed (earned, granted or spent). {@code delta} is negative for spending; {@code reason} is
 * a short id such as "match", "purchase" or "admin" so other plugins can tell them apart. The player may be offline.
 */
public class CoinsChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final long delta;
    private final long balance;
    private final String reason;

    public CoinsChangedEvent(UUID player, long delta, long balance, String reason) {
        this.player = player;
        this.delta = delta;
        this.balance = balance;
        this.reason = reason;
    }

    public UUID getPlayer() {
        return player;
    }

    public long getDelta() {
        return delta;
    }

    /** The balance after the change. */
    public long getBalance() {
        return balance;
    }

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
