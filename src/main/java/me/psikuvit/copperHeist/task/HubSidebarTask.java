package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.ui.SidebarService;
import org.bukkit.scheduler.BukkitRunnable;

/** Refreshes the hub scoreboard for players who aren't in a match. */
public class HubSidebarTask extends BukkitRunnable {

    private final SidebarService target;

    public HubSidebarTask(SidebarService target) {
        this.target = target;
    }

    @Override
    public void run() {
        target.updateHub();
    }
}
