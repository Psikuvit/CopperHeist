package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.arena.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

import java.util.Set;

/**
 * One kind of loot from loot.yml. {@code zones} says which arena loot points may roll it,
 * {@code weight} is its share of those rolls (0 keeps it out of random rolls entirely,
 * as for the relic) and {@code nameTemplate} is MiniMessage with an optional {value} placeholder.
 */
public record LootTierDefinition(String id, Material material, int value, int weight, Set<Arena.LootZone> zones,
                                 String nameTemplate, boolean relic, Integer customModelData, boolean glint) {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public Component displayName(int currentValue) {
        return MINI.deserialize(nameTemplate.replace("{value}", String.valueOf(currentValue)))
                .decoration(TextDecoration.ITALIC, false);
    }
}
