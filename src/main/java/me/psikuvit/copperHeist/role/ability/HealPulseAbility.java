package me.psikuvit.copperHeist.role.ability;

import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/** Heals the user and teammates within {@code radius} by {@code amount} health points. */
public class HealPulseAbility implements RoleAbility {

    @Override
    public void activate(AbilityContext context) {
        double amount = context.spec().number("amount", 6);
        double radius = context.spec().number("radius", 5);
        Player user = context.player();
        for (Player other : user.getWorld().getPlayers()) {
            GamePlayer gp = context.game().getGamePlayer(other.getUniqueId());
            if (gp == null || gp.getTeam() != context.gamePlayer().getTeam()) continue;
            if (other.getLocation().distanceSquared(user.getLocation()) > radius * radius) continue;
            var max = other.getAttribute(Attribute.MAX_HEALTH);
            double cap = max == null ? 20.0 : max.getValue();
            other.setHealth(Math.min(cap, other.getHealth() + amount));
        }
    }

    @Override
    public String defaultMessageKey() {
        return "actionbar.ability-heal";
    }
}
