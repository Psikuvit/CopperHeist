package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.heist.DockLockManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Runs while a raider picks an enemy dock chest; interrupted if they wander off or the match stops. */
public class LockpickTask extends BukkitRunnable {

    private final CopperHeist plugin;
    private final Game game;
    private final DockLockManager locks;
    private final Player player;
    private final Location chestLocation;
    private final Location startLocation;
    private final int totalTicks;
    private final double maxDriftSquared;
    private int elapsedTicks;

    public LockpickTask(CopperHeist plugin, Game game, DockLockManager locks, Player player, Location chestLocation, int totalTicks) {
        this.plugin = plugin;
        this.game = game;
        this.locks = locks;
        this.player = player;
        this.chestLocation = chestLocation;
        this.startLocation = player.getLocation();
        this.totalTicks = totalTicks;
        double drift = plugin.settings().getDouble("dock.max-drift", 1.5);
        this.maxDriftSquared = drift * drift;
    }

    @Override
    public void run() {
        if (!player.isOnline() || !game.isActive()
                || !player.getWorld().equals(startLocation.getWorld())
                || player.getLocation().distanceSquared(startLocation) > maxDriftSquared) {
            cancel();
            locks.finish(player, chestLocation, false);
            if (player.isOnline()) player.sendActionBar(plugin.getMessageService().get(player, "dock.interrupted"));
            return;
        }
        if (elapsedTicks >= totalTicks) {
            cancel();
            player.sendActionBar(plugin.getMessageService().get(player, "dock.unlocked"));
            locks.finish(player, chestLocation, true);
            return;
        }
        player.sendActionBar(plugin.getMessageService().get(player, "dock.picking", "percent", elapsedTicks * 100 / totalTicks));
        elapsedTicks += 5;
    }
}
