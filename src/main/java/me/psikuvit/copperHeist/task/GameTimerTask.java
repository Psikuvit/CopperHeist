package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

/** The once-a-second heartbeat that drives a match's state machine. */
public class GameTimerTask extends BukkitRunnable {

    private final Game target;

    public GameTimerTask(Game target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.tick();
    }
}
