package me.psikuvit.copperHeist.npc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.EnumMap;
import java.util.Map;

/**
 * What an NPC look wears and holds, read from its options - used by the armor stand and Mannequin providers:
 * <pre>
 *   head: DIAMOND_HELMET       any material for the head
 *   skull: {type, value, signature}   a player head instead (type: player-name | uuid | texture); wins over head
 *   armor: {chest, legs, feet}        materials; color: "#RRGGBB" dyes leather pieces
 *   main-hand / off-hand: MATERIAL    what it holds
 * </pre>
 */
final class NpcGear {

    private NpcGear() {
    }

    static Map<EquipmentSlot, ItemStack> items(NpcSpec spec) {
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        Color dye = dye(spec.option("armor.color"));

        ItemStack head = skull(spec);
        if (head == null) head = stack(spec.option("head"), dye);
        put(items, EquipmentSlot.HEAD, head);
        put(items, EquipmentSlot.CHEST, stack(spec.option("armor.chest"), dye));
        put(items, EquipmentSlot.LEGS, stack(spec.option("armor.legs"), dye));
        put(items, EquipmentSlot.FEET, stack(spec.option("armor.feet"), dye));
        put(items, EquipmentSlot.HAND, stack(spec.option("main-hand"), null));
        put(items, EquipmentSlot.OFF_HAND, stack(spec.option("off-hand"), null));
        return items;
    }

    private static void put(Map<EquipmentSlot, ItemStack> items, EquipmentSlot slot, ItemStack stack) {
        if (stack != null) items.put(slot, stack);
    }

    private static ItemStack skull(NpcSpec spec) {
        ResolvableProfile profile = NpcSkins.profile(spec.option("skull.type"), spec.option("skull.value"), spec.option("skull.signature"));
        if (profile == null) return null;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.setData(DataComponentTypes.PROFILE, profile);
        return head;
    }

    private static ItemStack stack(String material, Color dye) {
        if (material == null || material.isBlank()) return null;
        Material matched = Material.matchMaterial(material);
        if (matched == null) return null;
        ItemStack stack = new ItemStack(matched);
        if (dye != null && stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(dye);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Color dye(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Color.fromRGB(Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
