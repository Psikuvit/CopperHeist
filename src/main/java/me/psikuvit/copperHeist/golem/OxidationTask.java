package me.psikuvit.copperHeist.golem;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Advances each golem's WeatheringCopperState over time. Speed
 * itself is read live off the entity state by GolemManager#currentSpeed;
 * this task only owns the aging clock and the stage transition.
 */
public class OxidationTask extends BukkitRunnable {

    private final Game game;

    public OxidationTask(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        if (!game.isActive()) return;
        long now = System.currentTimeMillis();
        double agingMultiplier = game.getState() == GameState.FINAL_RUSH
                ? game.getPlugin().getConfig().getDouble("final-rush.aging-multiplier", 2.0) : 1.0;
        List<HeistGolem> golems = game.getGolemManager().all().stream().toList();
        for (HeistGolem golem : golems) {
            if (golem.getEntity().isDead()) continue;

            if (golem.isWaxed()) {
                if (now >= golem.getWaxedUntilMillis()) game.getGolemManager().unwax(golem);
                continue;
            }

            WeatheringCopperState state = golem.getEntity().getWeatheringState();
            if (state == WeatheringCopperState.OXIDIZED) continue;
            if ((now - golem.getStageChangedAtMillis()) * agingMultiplier < golem.getStageDurationMillis()) continue;

            WeatheringCopperState next = GolemManager.nextStage(state);
            golem.getEntity().setWeatheringState(next);
            golem.setStageChangedAtMillis(now);
            golem.setStageDurationMillis(rollDuration());
            game.getGolemManager().updateLabel(golem);

            if (next == WeatheringCopperState.OXIDIZED) {
                int index = game.getTeam(golem.getTeam()).getGolems().indexOf(golem) + 1;
                Component msg = Component.text(golem.getTeam().displayName() + " golem #" + index
                        + " has fully oxidized! Scrape it with an axe.", NamedTextColor.RED);
                for (Player player : game.onlinePlayers()) player.sendMessage(msg);
            }
        }
    }

    private long rollDuration() {
        long base = game.getPlugin().getConfig().getLong("golems.aging-seconds", 210) * 1000L;
        long jitter = game.getPlugin().getConfig().getLong("golems.aging-jitter-seconds", 20) * 1000L;
        long jittered = base + (long) ((Math.random() * 2 - 1) * jitter);
        return Math.max(1000L, jittered);
    }
}
