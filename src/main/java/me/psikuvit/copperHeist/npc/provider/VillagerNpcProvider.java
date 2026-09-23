package me.psikuvit.copperHeist.npc.provider;

import me.psikuvit.copperHeist.npc.NpcHandle;
import me.psikuvit.copperHeist.npc.NpcProvider;
import me.psikuvit.copperHeist.npc.NpcSpec;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;

import java.util.Locale;

/**
 * A frozen, invulnerable villager, customised by profession (which decides its outfit and the block it appears to work at). Options
 * (look - a navigator look or a shop keeper cosmetic - or npc.villager in config.yml): profession (librarian, armorer, toolsmith, cartographer, cleric, farmer ...),
 * biome (plains, desert, jungle, savanna, snow, swamp, taiga - the villager's clothing style), level (1-5, the badge on its outfit) and
 * baby (true for a child villager).
 */
public class VillagerNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        Villager.Profession profession = lookup(Registry.VILLAGER_PROFESSION, spec.string("profession", "npc.villager.profession", "librarian"));
        Villager.Type biome = lookup(Registry.VILLAGER_TYPE, spec.option("biome"));
        int level = spec.look() == null ? 1 : Math.clamp(spec.look().integer("level", 1), 1, 5);
        boolean baby = spec.look() != null && spec.look().bool("baby", false);

        Villager villager = spec.location().getWorld().spawn(spec.location(), Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.customName(spec.name());
            entity.setCustomNameVisible(true);
            if (profession != null) entity.setProfession(profession);
            if (biome != null) entity.setVillagerType(biome);
            entity.setVillagerLevel(level);
            if (baby) {
                entity.setBaby();
                entity.setAgeLock(true);
            }
        });
        return NpcHandle.of(villager);
    }

    /** Finds a registry entry by name ("armorer" or "minecraft:armorer"); null when it is not given or unknown. */
    private static <T extends Keyed> T lookup(Registry<T> registry, String name) {
        if (name == null || name.isBlank()) return null;
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : registry.get(key);
    }
}
