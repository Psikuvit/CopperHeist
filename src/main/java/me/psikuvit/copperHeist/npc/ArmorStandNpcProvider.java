package me.psikuvit.copperHeist.npc;

import org.bukkit.entity.ArmorStand;

/** An armor stand - light on the server, and supports npc.armor-stand.small / arms. */
public class ArmorStandNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        boolean small = spec.settings().getBoolean("npc.armor-stand.small", false);
        boolean arms = spec.settings().getBoolean("npc.armor-stand.arms", true);
        ArmorStand stand = spec.location().getWorld().spawn(spec.location(), ArmorStand.class, entity -> {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setBasePlate(false);
            entity.setSmall(small);
            entity.setArms(arms);
            entity.setSilent(true);
                entity.customName(spec.name());
            entity.setCustomNameVisible(true);
        });
        return NpcHandle.of(stand);
    }
}
