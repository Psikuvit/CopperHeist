package me.psikuvit.copperHeist.task.golem;

import org.bukkit.entity.CopperGolem;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.List;

/** Ends the Saboteur's golem reveal by removing the glow from the golems it lit up. */
public class RevealEndTask extends BukkitRunnable {

    private final List<CopperGolem> revealed;

    public RevealEndTask(List<CopperGolem> revealed) {
        this.revealed = revealed;
    }

    @Override
    public void run() {
        for (CopperGolem golem : revealed) {
            if (!golem.isDead()) golem.setGlowing(false);
        }
    }
}
