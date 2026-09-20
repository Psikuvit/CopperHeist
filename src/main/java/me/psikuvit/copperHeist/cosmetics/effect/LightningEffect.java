package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;

import java.util.Set;

/**
 * A lightning bolt that only looks like one ({@code effect: lightning}): no damage, no fire, no charged creepers. It is a world effect, so
 * everyone nearby sees and hears it. No params.
 */
public class LightningEffect implements EffectProvider {

    @Override
    public String id() {
        return "lightning";
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.KILL_EFFECT, CosmeticCategory.VICTORY);
    }

    @Override
    public void play(EffectContext context) {
        context.location().getWorld().strikeLightningEffect(context.location());
    }
}
