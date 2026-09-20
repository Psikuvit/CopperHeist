package me.psikuvit.copperHeist.cosmetics;

import java.util.Set;

/**
 * Something that can play a cosmetic: a particle trail, a lightning strike, a firework spiral ... Built-ins are registered by the
 * plugin; other plugins add theirs through the API. A cosmetics.yml entry picks one by its {@link #id()} and passes it {@code params}.
 * Implementations must be cheap and must never affect gameplay (no damage, no knockback, no blocks) - cosmetics are purely visual.
 */
public interface EffectProvider {

    /** The id used by {@code effect:} in cosmetics.yml. */
    String id();

    /** The categories this effect makes sense for; a cosmetic using it in another category is rejected when loading. */
    Set<CosmeticCategory> categories();

    /** Throws {@link IllegalArgumentException} (with a readable message) if the cosmetic's params are unusable; checked when loading. */
    default void validate(CosmeticDefinition cosmetic) {
    }

    /**
     * Plays the effect once (or, for a trail, starts following {@code context.entity()}). Called on the main thread. In a menu preview
     * {@code context.entity()} is null and only the previewing player is a viewer: play something short at {@code context.location()}
     * (a trail can draw a brief line in front of the player).
     */
    void play(EffectContext context);
}
