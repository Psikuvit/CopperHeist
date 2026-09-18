package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.loot.LootSpawner;
import org.bukkit.scheduler.BukkitRunnable;

/** Refills empty loot points and applies the unclaimed-loot bonus. */
public class LootRefillTask extends BukkitRunnable {

    private final LootSpawner target;

    public LootRefillTask(LootSpawner target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.tick();
    }
}
