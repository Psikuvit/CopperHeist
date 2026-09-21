package me.psikuvit.copperHeist.npc;

import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * An armor stand - light on the server, customised by what it wears: a head (any block or a player head with a skin), armor pieces
 * (leather can be dyed) and held items. Options (npcs.yml look, or npc.armor-stand in config.yml): small, arms, base-plate, plus the
 * gear options described in {@link NpcGear}. Players can't take or change what it wears.
 */
public class ArmorStandNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        boolean small = spec.bool("small", "npc.armor-stand.small", false);
        boolean arms = spec.bool("arms", "npc.armor-stand.arms", true);
        boolean basePlate = spec.bool("base-plate", "npc.armor-stand.base-plate", false);
        Map<EquipmentSlot, ItemStack> gear = NpcGear.items(spec);

        ArmorStand stand = spec.location().getWorld().spawn(spec.location(), ArmorStand.class, entity -> {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setBasePlate(basePlate);
            entity.setSmall(small);
            entity.setArms(arms);
            entity.setSilent(true);
            entity.customName(spec.name());
            entity.setCustomNameVisible(true);
            for (Map.Entry<EquipmentSlot, ItemStack> piece : gear.entrySet()) entity.setItem(piece.getKey(), piece.getValue());
            // Set after the gear so it can't be swapped: right-clicking an armor stand normally lets players take its items.
            entity.setDisabledSlots(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                    EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);
        });
        return NpcHandle.of(stand);
    }
}
