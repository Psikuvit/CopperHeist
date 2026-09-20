package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A skin for the team's shop NPC ({@code effect: npc-skin}); it needs the Mannequin NPC type ({@code npc.type: mannequin}). Params:
 * <pre>
 *   type: player-name    player-name | uuid | texture
 *   value: Notch         the name, the UUID, or the base64 texture value
 *   signature: ""        texture only
 * </pre>
 * Nothing is "played": when a match starts, the shop NPC of each team is spawned wearing the rarest NPC skin someone on that team has
 * equipped, and the Mannequin provider reads these params.
 */
public class NpcSkinEffect implements EffectProvider {

    private static final Set<String> TYPES = Set.of("player-name", "uuid", "texture");

    @Override
    public String id() {
        return "npc-skin";
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.NPC);
    }

    @Override
    public void validate(CosmeticDefinition cosmetic) {
        Map<String, Object> params = cosmetic.params();
        String type = String.valueOf(params.getOrDefault("type", "player-name")).toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("params.type must be one of " + String.join(", ", TYPES));
        if (String.valueOf(params.getOrDefault("value", "")).isBlank()) throw new IllegalArgumentException("params.value is required");
    }

    @Override
    public void play(EffectContext context) {
        // The skin is applied when the NPC is spawned, not played.
    }
}
