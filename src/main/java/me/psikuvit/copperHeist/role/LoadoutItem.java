package me.psikuvit.copperHeist.role;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** One entry in a role's starting kit: either a plain material item or a reference to a shop item by id. */
public record LoadoutItem(Material material, String shopItem, int amount, String name, List<String> lore,
                          Map<String, Integer> enchants, boolean offhand) {
}
