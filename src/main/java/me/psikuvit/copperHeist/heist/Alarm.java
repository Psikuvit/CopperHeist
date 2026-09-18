package me.psikuvit.copperHeist.heist;

import me.psikuvit.copperHeist.game.Team;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;

/** One placed alarm: an invisible {@link Interaction} hitbox enemies can destroy, guarding a location near its team's spawn. */
public class Alarm {

    private final Team team;
    private final Location location;
    private final Interaction hitbox;

    public Alarm(Team team, Location location, Interaction hitbox) {
        this.team = team;
        this.location = location;
        this.hitbox = hitbox;
    }

    public Team getTeam() {
        return team;
    }

    public Location getLocation() {
        return location;
    }

    public Interaction getHitbox() {
        return hitbox;
    }
}
