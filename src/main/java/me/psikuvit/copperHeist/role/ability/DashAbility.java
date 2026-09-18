package me.psikuvit.copperHeist.role.ability;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** A short burst of speed in the direction the user is facing ({@code power}, default 1.2). */
public class DashAbility implements RoleAbility {

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        double power = context.spec().number("power", 1.2);
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) return;
        player.setVelocity(direction.normalize().multiply(power).setY(0.25));
    }

    @Override
    public String defaultMessageKey() {
        return "actionbar.ability-dash";
    }
}
