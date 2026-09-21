package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.game.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

import java.util.Map;

/**
 * What an NPC provider needs to place one NPC: where, its name tag, whose it is (null for a hub NPC) and the config to read.
 * {@code skin} (may be null) is the params of an equipped shop-skin cosmetic (type, value, signature); the Mannequin provider wears it
 * instead of any other skin. {@code look} (may be null) is the named appearance from a looks file; a provider takes each option from the look
 * first and falls back to the matching {@code npc.*} setting in config.yml, so servers without looks behave as before.
 */
public record NpcSpec(Location location, Component name, Team team, Settings settings, Map<String, Object> skin, NpcLook look) {

    public NpcSpec(Location location, Component name, Team team, Settings settings) {
        this(location, name, team, settings, null, null);
    }

    public NpcSpec(Location location, Component name, Team team, Settings settings, Map<String, Object> skin) {
        this(location, name, team, settings, skin, null);
    }

    /** An option from the look, else the config.yml setting at {@code settingsPath}, else the fallback. */
    public String string(String key, String settingsPath, String fallback) {
        if (look != null && look.has(key)) return look.string(key);
        return settingsPath == null ? fallback : settings.getString(settingsPath, fallback);
    }

    public boolean bool(String key, String settingsPath, boolean fallback) {
        if (look != null && look.has(key)) return look.bool(key, fallback);
        return settingsPath == null ? fallback : settings.getBoolean(settingsPath, fallback);
    }

    /** An option that only exists on a look (no config.yml equivalent), or null. */
    public String option(String key) {
        return look == null ? null : look.string(key);
    }
}
