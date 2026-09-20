package me.psikuvit.copperHeist.cosmetics;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * One cosmetic from cosmetics.yml.
 *
 * @param effect        id of the {@link EffectProvider} that plays it (null for titles, which are only text)
 * @param name          MiniMessage; the menu wraps it in the rarity colour when it has none of its own
 * @param price         coins to buy it; 0 means it can't be bought (a reward, a permission or free)
 * @param level         progression level needed to buy it (or, for a free cosmetic, to get it); 0 = none
 * @param permission    a permission that grants it without buying (null = none). {@code copperheist.cosmetic.<id>} and
 *                      {@code copperheist.cosmetic.*} always work too
 * @param free          everyone owns it from the start
 * @param hidden        not listed in menus until the player owns it
 * @param params        settings for the effect (particle, colour, ...) - each provider documents its own
 */
public record CosmeticDefinition(String id, CosmeticCategory category, String effect, String name, List<String> description,
                                 Material icon, Rarity rarity, long price, int level, String permission, boolean free,
                                 boolean hidden, Map<String, Object> params) {

    /** The permission node that grants this cosmetic to anyone who has it. */
    public String idPermission() {
        return "copperheist.cosmetic." + id;
    }

    public boolean purchasable() {
        return price > 0;
    }
}
