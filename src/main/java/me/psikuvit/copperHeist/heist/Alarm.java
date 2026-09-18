package me.psikuvit.copperHeist.heist;

import me.psikuvit.copperHeist.game.Team;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;

/**
 * One placed alarm: an invisible {@link Interaction} hitbox enemies can destroy, guarding a location near its team's spawn.
 */
public record Alarm(Team team, Location location, Interaction hitbox) {

}
