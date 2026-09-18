package me.psikuvit.copperHeist.role.ability;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.task.RevealEndTask;
import org.bukkit.entity.CopperGolem;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes every enemy golem glow for {@code duration-seconds}. Without a packet
 * library the glow is visible to everyone, not just the user's team - a true
 * per-viewer reveal isn't reachable with plain Bukkit API.
 */
public class RevealGolemsAbility implements RoleAbility {

    @Override
    public void activate(AbilityContext context) {
        Team enemy = context.gamePlayer().getTeam().opposite();
        List<CopperGolem> revealed = new ArrayList<>();
        for (var golem : context.game().getTeam(enemy).getGolems()) {
            golem.getEntity().setGlowing(true);
            revealed.add(golem.getEntity());
        }
        long ticks = (long) (context.spec().number("duration-seconds", 10) * 20);
        new RevealEndTask(revealed).runTaskLater(context.plugin(), ticks);
    }

    @Override
    public String defaultMessageKey() {
        return "actionbar.saboteur-reveal";
    }
}
