package me.psikuvit.copperHeist.npc;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;

import java.util.Locale;

/** A frozen, invulnerable villager. Config: npc.villager.profession (e.g. librarian, farmer, cleric). */
public class VillagerNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        String profession = spec.settings().getString("npc.villager.profession", "librarian");
        NamespacedKey key = NamespacedKey.fromString(profession.toLowerCase(Locale.ROOT));
        Villager.Profession resolved = key == null ? null : Registry.VILLAGER_PROFESSION.get(key);

        Villager villager = spec.location().getWorld().spawn(spec.location(), Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
                entity.customName(spec.name());
            entity.setCustomNameVisible(true);
            if (resolved != null) entity.setProfession(resolved);
        });
        return NpcHandle.of(villager);
    }
}
