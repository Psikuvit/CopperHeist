package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import me.psikuvit.copperHeist.npc.NpcLook;

import java.util.Locale;
import java.util.Set;

/**
 * How the team's shop keepers look ({@code effect: shop-keeper}, category {@code npc}). The params are the whole appearance: which kind of
 * NPC it is and how it is dressed.
 * <pre>
 *   type: villager | mannequin | armor-stand | interaction    the kind of NPC (required)
 *   name: "{team} Armorer"                                    MiniMessage name tag; {team} is the team's name (optional)
 *   profession, biome, level, baby                            villager
 *   skin: {type: player-name|uuid|texture, value, signature}  mannequin
 *   small, arms, base-plate                                   armor stand
 *   head, skull, armor: {chest, legs, feet, color}, main-hand, off-hand    gear (armor stands and mannequins)
 * </pre>
 * Nothing is "played": when a match starts, each team's shop keepers are built from the rarest shop-keeper cosmetic someone on the team has
 * equipped. Without one they use the plain {@code npc.*} settings in config.yml.
 */
public class ShopKeeperEffect implements EffectProvider {

    public static final String ID = "shop-keeper";

    private static final Set<String> TYPES = Set.of("villager", "mannequin", "armor-stand", "interaction");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.NPC);
    }

    @Override
    public void validate(CosmeticDefinition cosmetic) {
        Object type = cosmetic.params().get("type");
        if (type == null || !TYPES.contains(String.valueOf(type).toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("params.type must be one of " + String.join(", ", TYPES));
        }
    }

    @Override
    public void play(EffectContext context) {
        // The look is applied when the shop keeper is spawned, not played.
    }

    /** The appearance a shop-keeper cosmetic describes. */
    public static NpcLook lookOf(CosmeticDefinition cosmetic) {
        return NpcLook.fromParams(cosmetic.id(), cosmetic.params());
    }
}
