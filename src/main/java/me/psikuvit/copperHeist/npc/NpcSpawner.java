package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** The one place an NPC is built: resolves the provider for a type and falls back to a plain villager if that provider fails. */
public final class NpcSpawner {

    private NpcSpawner() {
    }

    /** Spawns {@code spec} with the provider registered for {@code type}; null when the provider places nothing. */
    public static NpcHandle spawn(CopperHeist plugin, String type, NpcSpec spec, String what) {
        try {
            return plugin.providers().npc().resolve(type).spawn(spec);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("NPC type '" + type + "' failed for " + what + " (" + ex + ") - using a villager.");
            return new VillagerNpcProvider().spawn(new NpcSpec(spec.location(), spec.name(), spec.team(), spec.settings()));
        }
    }

    /** The clickable entity and everything that goes with it. */
    public static List<Entity> entities(NpcHandle handle) {
        List<Entity> all = new ArrayList<>();
        all.add(handle.clickable());
        all.addAll(handle.extras());
        return all;
    }
}
