package me.psikuvit.copperHeist.heist;

import me.psikuvit.copperHeist.game.Team;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;

/**
 * One placed alarm: an invisible {@link Interaction} hitbox enemies can destroy, guarding a location near its
 * team's spawn. {@code box} is a glowing marker only the owning team is shown (null when disabled).
 */
public record Alarm(Team team, Location location, Interaction hitbox, BlockDisplay box) {

}
