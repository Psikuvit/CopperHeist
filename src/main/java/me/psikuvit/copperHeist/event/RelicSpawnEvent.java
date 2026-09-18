package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when the Relic appears on the ground at one of the arena's relic points. */
public class RelicSpawnEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;

    public RelicSpawnEvent(Game game, Location location) {
        super(game);
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
