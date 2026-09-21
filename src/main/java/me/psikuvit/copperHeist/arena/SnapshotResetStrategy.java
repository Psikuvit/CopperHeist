package me.psikuvit.copperHeist.arena;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.task.SnapshotRestoreTask;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * reset.method: snapshot - after the usual entity/chest cleanup, every block inside the arena bounds is
 * restored from the copy saved by "arena snapshot", a few thousand per tick so a big arena never lags the
 * server. Falls back to plain cleanup (with a warning) if the arena has no snapshot yet.
 */
public class SnapshotResetStrategy implements ArenaResetStrategy {

    private final CopperHeist plugin;
    private final ArenaResetter cleaner;

    public SnapshotResetStrategy(CopperHeist plugin, ArenaResetter cleaner) {
        this.plugin = plugin;
        this.cleaner = cleaner;
    }

    @Override
    public void reset(Arena arena) {
        cleaner.reset(arena);

        File file = plugin.getArenaManager().snapshotFile(arena);
        if (!file.exists()) {
            plugin.getLogger().warning("Arena '" + arena.getName() + "' has no snapshot - run /ch arena snapshot "
                    + arena.getName() + " to enable block restoring. Only entities were cleaned.");
            return;
        }
        try {
            ArenaSnapshot snapshot = ArenaSnapshot.read(file);
            int perTick = Math.max(1000, plugin.settings().getInt("reset.snapshot.blocks-per-tick", 20000));
            if (plugin.getGameManager().isShuttingDown()) {
                // Scheduled tasks don't run once the plugin is disabled, so put every block back right now.
                for (int position = 0; position < snapshot.blockCount(); position++) position = snapshot.restore(position, perTick);
            } else {
                new SnapshotRestoreTask(snapshot, perTick).runTaskTimer(plugin, 1L, 1L);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read the snapshot for " + arena.getName(), ex);
        }
    }
}
