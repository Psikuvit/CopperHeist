package me.psikuvit.copperHeist.task.heist;

import me.psikuvit.copperHeist.heist.VaultDrillManager;
import org.bukkit.scheduler.BukkitRunnable;

/** Advances active vault drills while an attacker stands nearby. */
public class VaultDrillTask extends BukkitRunnable {

    private final VaultDrillManager target;

    public VaultDrillTask(VaultDrillManager target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.tick();
    }
}
