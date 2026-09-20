package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A firework that explodes on the spot ({@code effect: firework}) - kill, victory and join effects. Params:
 * <pre>
 *   colors: ["#FF8800", "#FFFFFF"]   one or more #RRGGBB colours
 *   fade: ["#000000"]                optional colours it fades to
 *   type: BALL_LARGE                 BALL | BALL_LARGE | STAR | BURST | CREEPER
 *   trail: false                     leave a trail
 *   flicker: false                   twinkle
 * </pre>
 * Everyone in range sees it (it is a real firework entity), but it is tagged so it can never hurt anyone.
 */
public class FireworkBurstEffect implements EffectProvider {

    @Override
    public String id() {
        return "firework";
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.KILL_EFFECT, CosmeticCategory.VICTORY, CosmeticCategory.JOIN);
    }

    @Override
    public void validate(CosmeticDefinition cosmetic) {
        build(cosmetic.params());
    }

    @Override
    public void play(EffectContext context) {
        FireworkEffect effect = build(context.cosmetic().params());
        Location at = context.location().clone().add(0, 0.5, 0);
        at.getWorld().spawn(at, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(effect);
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            Pdc.set(firework, PdcKeys.COSMETIC, "firework");
            firework.setSilent(false);
        }).detonate();
    }

    /** Builds the firework effect from params, or throws IllegalArgumentException with a readable reason. */
    static FireworkEffect build(Map<String, Object> params) {
        List<Color> colors = colors(params.get("colors"), "colors");
        if (colors.isEmpty()) colors = List.of(Color.WHITE);
        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(String.valueOf(params.getOrDefault("type", "BALL_LARGE")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown firework type '" + params.get("type") + "'");
        }
        return FireworkEffect.builder().with(type).withColor(colors).withFade(colors(params.get("fade"), "fade"))
                .trail(Boolean.parseBoolean(String.valueOf(params.getOrDefault("trail", false))))
                .flicker(Boolean.parseBoolean(String.valueOf(params.getOrDefault("flicker", false)))).build();
    }

    private static List<Color> colors(Object raw, String key) {
        List<Color> colors = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) colors.add(ParticleSpec.parseColor(String.valueOf(entry)));
        } else if (raw != null) {
            throw new IllegalArgumentException("params." + key + " must be a list of #RRGGBB colours");
        }
        return colors;
    }
}
