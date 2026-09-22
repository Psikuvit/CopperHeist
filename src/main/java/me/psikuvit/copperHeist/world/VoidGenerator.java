package me.psikuvit.copperHeist.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jspecify.annotations.NonNull;

import java.util.Random;

/** Generates nothing at all: an empty world to build an arena in. Players spawn on a small platform at the world's spawn point. */
public class VoidGenerator extends ChunkGenerator {

    /** Where the spawn platform's centre is. */
    public static final int SPAWN_Y = 100;

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(@NonNull World world, @NonNull Random random) {
        return new Location(world, 0.5, SPAWN_Y + 1, 0.5);
    }
}
