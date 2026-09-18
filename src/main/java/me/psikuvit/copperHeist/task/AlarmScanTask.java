package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.heist.AlarmManager;
import org.bukkit.scheduler.BukkitRunnable;

/** Checks every placed alarm for enemies walking into range. */
public class AlarmScanTask extends BukkitRunnable {

    private final AlarmManager target;

    public AlarmScanTask(AlarmManager target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.tick();
    }
}
