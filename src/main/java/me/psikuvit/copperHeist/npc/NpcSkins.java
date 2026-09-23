package me.psikuvit.copperHeist.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

import java.util.Locale;
import java.util.UUID;

/** Builds the player profile (a skin) for Mannequins and player heads from a type and a value. */
public final class NpcSkins {

    private NpcSkins() {
    }

    /**
     * @param type      player-name | uuid | texture
     * @param value     the name, the UUID, or the base64 texture value
     * @param signature texture only; blank for none
     * @return the profile, or null when there is no value or it is unusable - a bad skin must never stop an NPC from spawning
     */
    public static ResolvableProfile profile(String type, String value, String signature) {
        if (value == null || value.isBlank()) return null;
        try {
            var builder = ResolvableProfile.resolvableProfile();
            switch (type == null ? "player-name" : type.toLowerCase(Locale.ROOT)) {
                case "uuid" -> builder.uuid(UUID.fromString(value));
                case "texture" -> {
                    builder.name("CopperHeist");
                    builder.addProperty(new ProfileProperty("textures", value, signature == null || signature.isBlank() ? null : signature));
                }
                default -> builder.name(value);
            }
            return builder.build();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
