package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.EffectRegistry;

/** The effects that ship with the plugin. Others are added through {@code CopperHeistAPI.registerCosmeticEffect}. */
public final class CosmeticEffects {

    private CosmeticEffects() {
    }

    public static void registerAll(CopperHeist plugin, EffectRegistry registry) {
        registry.register(new ParticleTrailEffect(plugin));
        registry.register(new ParticleBurstEffect(plugin));
        registry.register(new FireworkBurstEffect());
        registry.register(new LightningEffect());
        registry.register(new GolemHatEffect());
        registry.register(new NpcSkinEffect());
    }
}
