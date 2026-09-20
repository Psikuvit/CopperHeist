package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.game.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

import java.util.Map;

/**
 * What an NPC provider needs to place one shop NPC: where, its name tag, whose it is and the config to read.
 * {@code skin} (may be null) is the params of an equipped shop-skin cosmetic (type, value, signature); the Mannequin provider
 * wears it instead of the skin from config.yml, other providers ignore it.
 */
public record NpcSpec(Location location, Component name, Team team, Settings settings, Map<String, Object> skin) {

    public NpcSpec(Location location, Component name, Team team, Settings settings) {
        this(location, name, team, settings, null);
    }
}
