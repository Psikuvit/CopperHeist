package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/** Fired when a player picks up loot last owned by the other team. */
public class LootStolenEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player thief;
    private final Team victimTeam;
    private final int value;

    public LootStolenEvent(Game game, Player thief, Team victimTeam, int value) {
        super(game);
        this.thief = thief;
        this.victimTeam = victimTeam;
        this.value = value;
    }

    public Player getThief() {
        return thief;
    }

    public Team getVictimTeam() {
        return victimTeam;
    }

    public int getValue() {
        return value;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
