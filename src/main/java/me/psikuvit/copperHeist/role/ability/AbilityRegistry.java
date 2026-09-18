package me.psikuvit.copperHeist.role.ability;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Ability id (as written in roles.yml) to implementation. Ships four; other plugins can register more. */
public final class AbilityRegistry {

    private final Map<String, RoleAbility> abilities = new HashMap<>();

    public AbilityRegistry() {
        register("invisibility", new InvisibilityAbility());
        register("reveal-golems", new RevealGolemsAbility());
        register("dash", new DashAbility());
        register("heal-pulse", new HealPulseAbility());
    }

    public void register(String id, RoleAbility ability) {
        abilities.put(id.toLowerCase(Locale.ROOT), ability);
    }

    public RoleAbility get(String id) {
        return abilities.get(id.toLowerCase(Locale.ROOT));
    }
}
