package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.arena.ArenaSnapshot;
import org.bukkit.scheduler.BukkitRunnable;

/** Restores an arena snapshot a slice of blocks per tick until it reaches the end. */
public class SnapshotRestoreTask extends BukkitRunnable {

    private final ArenaSnapshot snapshot;
    private final int blocksPerTick;
    private int position;

    public SnapshotRestoreTask(ArenaSnapshot snapshot, int blocksPerTick) {
        this.snapshot = snapshot;
        this.blocksPerTick = blocksPerTick;
    }

    @Override
    public void run() {
        position = snapshot.restore(position, blocksPerTick);
        if (position >= snapshot.blockCount()) cancel();
    }
}
