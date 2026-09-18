package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.heist.Alarm;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when an enemy player walks within range of a placed, off-cooldown alarm. */
public class AlarmTriggeredEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Alarm alarm;
    private final Player intruder;

    public AlarmTriggeredEvent(Game game, Alarm alarm, Player intruder) {
        super(game);
        this.alarm = alarm;
        this.intruder = intruder;
    }

    public Alarm getAlarm() {
        return alarm;
    }

    public Player getIntruder() {
        return intruder;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
