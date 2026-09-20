package me.psikuvit.copperHeist.cosmetics;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The effects that cosmetics can use, by id. The plugin registers its own; other plugins add more through the API. */
public class EffectRegistry {

    private final Map<String, EffectProvider> effects = new LinkedHashMap<>();

    public void register(EffectProvider provider) {
        effects.put(provider.id().toLowerCase(Locale.ROOT), provider);
    }

    public EffectProvider get(String id) {
        return id == null ? null : effects.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return effects.keySet();
    }
}
