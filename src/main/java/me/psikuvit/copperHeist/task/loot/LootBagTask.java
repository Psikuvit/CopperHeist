package me.psikuvit.copperHeist.task.loot;

import me.psikuvit.copperHeist.loot.LootBagManager;
import org.bukkit.scheduler.BukkitRunnable;

/** Expires unclaimed loot bags and refreshes their timer labels. */
public class LootBagTask extends BukkitRunnable {

    private final LootBagManager target;

    public LootBagTask(LootBagManager target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.tick();
    }
}
