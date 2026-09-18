package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.relic.RelicManager;
import org.bukkit.scheduler.BukkitRunnable;

/** Counts down to and announces the next relic spawn. */
public class RelicSpawnTask extends BukkitRunnable {

    private final RelicManager target;

    public RelicSpawnTask(RelicManager target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.tick();
    }
}
