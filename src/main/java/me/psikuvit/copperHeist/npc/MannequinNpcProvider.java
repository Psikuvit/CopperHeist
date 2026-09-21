package me.psikuvit.copperHeist.npc;

import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Map;

/**
 * A Mannequin - a player-shaped NPC with a real skin - customised by skin and gear. Options (look from shop-looks.yml or navigator-looks.yml, or npc.mannequin in
 * config.yml): skin.type (player-name | uuid | texture), skin.value, skin.signature (texture only), pose, immovable, show-name,
 * description (MiniMessage line under the name) and skin-parts.{cape,jacket,sleeves,pants,hat}; a look can also give it armor and held
 * items (see {@link NpcGear}). A skin from an equipped shop-skin cosmetic wins over the look's and config.yml's.
 */
public class MannequinNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        ResolvableProfile profile = profile(spec);
        Pose pose = parsePose(spec.string("pose", "npc.mannequin.pose", "STANDING"));
        String description = spec.string("description", "npc.mannequin.description", "");
        boolean showName = spec.bool("show-name", "npc.mannequin.show-name", true);
        boolean immovable = spec.bool("immovable", "npc.mannequin.immovable", true);
        Map<EquipmentSlot, ItemStack> gear = NpcGear.items(spec);

        Mannequin mannequin = spec.location().getWorld().spawn(spec.location(), Mannequin.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setImmovable(immovable);
            entity.customName(spec.name());
            entity.setCustomNameVisible(showName);
            entity.setDescription(description.isBlank() ? Component.empty() : Theme.mini().deserialize(description));
            if (pose != null && Mannequin.validPoses().contains(pose)) entity.setPose(pose);
            if (profile != null) entity.setProfile(profile);
            for (Map.Entry<EquipmentSlot, ItemStack> piece : gear.entrySet()) entity.getEquipment().setItem(piece.getKey(), piece.getValue());

            var parts = getSkinParts(spec, entity);
            entity.setSkinParts(parts);
        });
        return NpcHandle.of(mannequin);
    }

    private static SkinParts.@NonNull Mutable getSkinParts(NpcSpec spec, Mannequin entity) {
        var parts = entity.getSkinParts();
        parts.setCapeEnabled(spec.bool("skin-parts.cape", "npc.mannequin.skin-parts.cape", true));
        parts.setJacketEnabled(spec.bool("skin-parts.jacket", "npc.mannequin.skin-parts.jacket", true));
        parts.setLeftSleeveEnabled(spec.bool("skin-parts.sleeves", "npc.mannequin.skin-parts.sleeves", true));
        parts.setRightSleeveEnabled(spec.bool("skin-parts.sleeves", "npc.mannequin.skin-parts.sleeves", true));
        parts.setLeftPantsEnabled(spec.bool("skin-parts.pants", "npc.mannequin.skin-parts.pants", true));
        parts.setRightPantsEnabled(spec.bool("skin-parts.pants", "npc.mannequin.skin-parts.pants", true));
        parts.setHatsEnabled(spec.bool("skin-parts.hat", "npc.mannequin.skin-parts.hat", true));
        return parts;
    }

    /** Null means "leave the default skin". Priority: an equipped shop-skin cosmetic, then the look, then config.yml. */
    private ResolvableProfile profile(NpcSpec spec) {
        Map<String, Object> cosmetic = spec.skin();
        if (cosmetic != null) {
            return NpcSkins.profile(String.valueOf(cosmetic.getOrDefault("type", "player-name")), String.valueOf(cosmetic.getOrDefault("value", "")),
                    String.valueOf(cosmetic.getOrDefault("signature", "")));
        }
        return NpcSkins.profile(spec.string("skin.type", "npc.mannequin.skin.type", "player-name"),
                spec.string("skin.value", "npc.mannequin.skin.value", ""), spec.string("skin.signature", "npc.mannequin.skin.signature", ""));
    }

    private Pose parsePose(String name) {
        try {
            return Pose.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
