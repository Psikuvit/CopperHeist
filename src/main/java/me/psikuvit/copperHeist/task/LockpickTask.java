package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.heist.DockLockManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Runs while a raider picks an enemy dock chest; interrupted if they wander off or the match stops. */
public class LockpickTask extends BukkitRunnable {

    private static final double MAX_DRIFT_SQUARED = 2.25;

    private final CopperHeist plugin;
    private final Game game;
    private final DockLockManager locks;
    private final Player player;
    private final Location chestLocation;
    private final Location startLocation;
    private final int totalTicks;
    private int elapsedTicks;

    public LockpickTask(CopperHeist plugin, Game game, DockLockManager locks, Player player, Location chestLocation, int totalTicks) {
        this.plugin = plugin;
        this.game = game;
        this.locks = locks;
        this.player = player;
        this.chestLocation = chestLocation;
        this.startLocation = player.getLocation();
        this.totalTicks = totalTicks;
    }

    @Override
    public void run() {
        if (!player.isOnline() || !game.isActive()
                || !player.getWorld().equals(startLocation.getWorld())
                || player.getLocation().distanceSquared(startLocation) > MAX_DRIFT_SQUARED) {
            cancel();
            locks.finish(player, chestLocation, false);
            if (player.isOnline()) player.sendActionBar(plugin.getMessageService().get("dock.interrupted"));
            return;
        }
        if (elapsedTicks >= totalTicks) {
            cancel();
            player.sendActionBar(plugin.getMessageService().get("dock.unlocked"));
            locks.finish(player, chestLocation, true);
            return;
        }
        player.sendActionBar(plugin.getMessageService().get("dock.picking", "percent", elapsedTicks * 100 / totalTicks));
        elapsedTicks += 5;
    }
}
