package me.psikuvit.copperHeist.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A Mannequin - a player-shaped NPC with a real skin. All under npc.mannequin:
 * skin.type (player-name | uuid | texture), skin.value, skin.signature (texture only),
 * pose, immovable, show-name, description (MiniMessage line shown under the name)
 * and skin-parts.{cape,jacket,sleeves,pants,hat}.
 */
public class MannequinNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        Settings s = spec.settings();
        ResolvableProfile profile = buildProfile(s, spec.skin());
        Pose pose = parsePose(s.getString("npc.mannequin.pose", "STANDING"));
        String description = s.getString("npc.mannequin.description", "");
        boolean showName = s.getBoolean("npc.mannequin.show-name", true);
        boolean immovable = s.getBoolean("npc.mannequin.immovable", true);

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

            var parts = entity.getSkinParts();
            parts.setCapeEnabled(s.getBoolean("npc.mannequin.skin-parts.cape", true));
            parts.setJacketEnabled(s.getBoolean("npc.mannequin.skin-parts.jacket", true));
            parts.setLeftSleeveEnabled(s.getBoolean("npc.mannequin.skin-parts.sleeves", true));
            parts.setRightSleeveEnabled(s.getBoolean("npc.mannequin.skin-parts.sleeves", true));
            parts.setLeftPantsEnabled(s.getBoolean("npc.mannequin.skin-parts.pants", true));
            parts.setRightPantsEnabled(s.getBoolean("npc.mannequin.skin-parts.pants", true));
            parts.setHatsEnabled(s.getBoolean("npc.mannequin.skin-parts.hat", true));
            entity.setSkinParts(parts);
        });
        return NpcHandle.of(mannequin);
    }

    /** Null means "leave the default skin" - a bad skin config must never stop the NPC from spawning. */
    private ResolvableProfile buildProfile(Settings s, Map<String, Object> override) {
        // An equipped shop-skin cosmetic wins over the skin set in config.yml.
        String type = (override != null ? String.valueOf(override.getOrDefault("type", "player-name"))
                : s.getString("npc.mannequin.skin.type", "player-name")).toLowerCase(Locale.ROOT);
        String value = override != null ? String.valueOf(override.getOrDefault("value", "")) : s.getString("npc.mannequin.skin.value", "");
        String signatureValue = override != null ? String.valueOf(override.getOrDefault("signature", "")) : s.getString("npc.mannequin.skin.signature", "");
        if (value.isBlank()) return null;
        try {
            var builder = ResolvableProfile.resolvableProfile();
            switch (type) {
                case "uuid" -> builder.uuid(UUID.fromString(value));
                case "texture" -> {
                    builder.name("CopperHeist");
                    builder.addProperty(new ProfileProperty("textures", value, signatureValue.isBlank() ? null : signatureValue));
                }
                default -> builder.name(value);
            }
            return builder.build();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Pose parsePose(String name) {
        try {
            return Pose.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
