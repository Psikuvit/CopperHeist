package me.psikuvit.copperHeist.arena;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.World;

/** Decides which worlds the world-rules section of config.yml applies to (arena worlds, or every world). */
public final class WorldRules {

    private final CopperHeist plugin;

    public WorldRules(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public boolean blocksMobSpawns(World world) {
        return plugin.settings().getBoolean("world-rules.disable-mob-spawning", true) && applies(world);
    }

    public boolean pvpOnlyInGame(World world) {
        return plugin.settings().getBoolean("world-rules.pvp-only-in-game", true) && applies(world);
    }

    private boolean applies(World world) {
        if (world == null) return false;
        if ("all".equalsIgnoreCase(plugin.settings().getString("world-rules.scope", "arena"))) return true;
        for (Arena arena : plugin.getArenaManager().all()) {
            if (world.getName().equalsIgnoreCase(arena.getWorldName())) return true;
        }
        return false;
    }
}
