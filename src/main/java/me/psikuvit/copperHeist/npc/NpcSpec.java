package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.game.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

/** What an NPC provider needs to place one shop NPC: where, its name tag, whose it is and the config to read. */
public record NpcSpec(Location location, Component name, Team team, Settings settings) {
}
