package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;

/**
 * A small item worn on a golem's head ({@code effect: golem-hat}), carried as a passenger like the golem's label. Params:
 * <pre>
 *   material: GOLD_BLOCK   the item shown (any material)
 *   scale: 0.5             size (0.2-1.5)
 *   lift: 0.3              height above the head (0-1.5)
 * </pre>
 * The golem manager plays golem cosmetics every half second; this one only acts if the golem has no hat yet, so it is safe to repeat and
 * it puts a hat back if one was lost. The hat is tagged so it is removed with the golem.
 */
public class GolemHatEffect implements EffectProvider {

    @Override
    public String id() {
        return "golem-hat";
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.GOLEM);
    }

    @Override
    public void validate(CosmeticDefinition cosmetic) {
        material(cosmetic.params());
    }

    @Override
    public void play(EffectContext context) {
        Entity golem = context.entity();
        if (golem == null) return;
        for (Entity passenger : golem.getPassengers()) {
            if (passenger instanceof ItemDisplay && "golem-hat".equals(Pdc.get(passenger, PdcKeys.COSMETIC))) return;
        }
        Map<String, Object> params = context.cosmetic().params();
        Material material = material(params);
        float scale = (float) ParticleSpec.clamp(ParticleSpec.number(params, "scale", 0.5), 0.2, 1.5);
        float lift = (float) ParticleSpec.clamp(ParticleSpec.number(params, "lift", 0.3), 0, 1.5);

        ItemDisplay hat = golem.getWorld().spawn(golem.getLocation(), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(material));
            display.setTransformation(new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            Pdc.set(display, PdcKeys.COSMETIC, "golem-hat");
            String match = Pdc.get(golem, PdcKeys.MATCH_ID);
            if (match != null) Pdc.set(display, PdcKeys.MATCH_ID, match); // so a stale-entity sweep cleans it like the golem's label
        });
        golem.addPassenger(hat);
    }

    private static Material material(Map<String, Object> params) {
        Material material = Material.matchMaterial(String.valueOf(params.getOrDefault("material", "GOLD_BLOCK")));
        if (material == null) throw new IllegalArgumentException("params.material '" + params.get("material") + "' isn't a material");
        return material;
    }
}
