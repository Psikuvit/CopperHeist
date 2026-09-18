package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.event.Event;

/** Common base for this plugin's custom events - every one of them happens within one running match. */
public abstract class GameEvent extends Event {

    private final Game game;

    protected GameEvent(Game game) {
        this.game = game;
    }

    public Game getGame() {
        return game;
    }
}
