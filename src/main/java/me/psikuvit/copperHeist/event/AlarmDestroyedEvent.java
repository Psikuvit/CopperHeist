package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.heist.Alarm;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when an enemy player breaks a placed alarm. */
public class AlarmDestroyedEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Alarm alarm;
    private final Player destroyer;

    public AlarmDestroyedEvent(Game game, Alarm alarm, Player destroyer) {
        super(game);
        this.alarm = alarm;
        this.destroyer = destroyer;
    }

    public Alarm getAlarm() {
        return alarm;
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
