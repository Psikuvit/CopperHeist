package me.psikuvit.copperHeist.role;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Map;

/**
 * Everything about a role, loaded from roles.yml: how it looks in menus, its
 * starting kit, permanent potion effects, numeric passives (looked up by key
 * so new perks need no code change here) and an optional active ability.
 * {@code maxPerTeam} of 0 means "use the global roles.max-per-team".
 */
public record RoleDefinition(String id, String displayName, NamedTextColor color, Material icon, String description,
                             int maxPerTeam, Map<String, ArmorPiece> armor, List<LoadoutItem> items,
                             List<PotionEffect> effects, Map<String, Double> passives, AbilitySpec ability) {

    public double passive(String key, double def) {
        return passives.getOrDefault(key, def);
    }

    public boolean hasAbility() {
        return ability != null;
    }
}
