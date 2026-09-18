package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

/** Twice-a-second match housekeeping: golem labels, loot weight, spawn guarding. */
public class GameUiTask extends BukkitRunnable {

    private final Game target;

    public GameUiTask(Game target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.uiTick();
    }
}
