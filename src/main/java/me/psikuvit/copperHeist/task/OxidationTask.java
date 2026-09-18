package me.psikuvit.copperHeist.task;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.golem.GolemManager;
import me.psikuvit.copperHeist.golem.HeistGolem;
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
        double agingMultiplier = game.isFinalRushActive()
                ? game.getPlugin().settings().getDouble("final-rush.aging-multiplier", 2.0) : 1.0;
        List<HeistGolem> golems = game.getGolemManager().all().stream().toList();
        for (HeistGolem golem : golems) {
            if (golem.getEntity().isDead()) continue;

            if (golem.isWaxed()) {
                if (now >= golem.getWaxedUntilMillis()) game.getGolemManager().unwax(golem);
                continue;
            }

            WeatheringCopperState state = golem.getEntity().getWeatheringState();
            if (state == WeatheringCopperState.OXIDIZED) continue;
            double multiplier = agingMultiplier * decayMultiplier(golem, now);
            if ((now - golem.getStageChangedAtMillis()) * multiplier < golem.getStageDurationMillis()) continue;

            WeatheringCopperState next = GolemManager.nextStage(state);
            golem.getEntity().setWeatheringState(next);
            golem.setStageChangedAtMillis(now);
            golem.setStageDurationMillis(rollDuration());
            game.getGolemManager().updateLabel(golem);

            if (next == WeatheringCopperState.OXIDIZED) {
                int index = game.getTeam(golem.getTeam()).getGolems().indexOf(golem) + 1;
                for (Player player : game.onlinePlayers()) {
                    player.sendMessage(game.getPlugin().getMessageService().get(player, "golem.fully-oxidized",
                            "team", golem.getTeam().displayName(), "number", index));
                }
            }
        }
    }

    /** Anti-turtle: a team that hasn't delivered anything for a while sees its golems age faster. */
    private double decayMultiplier(HeistGolem golem, long now) {
        var config = game.getPlugin().settings();
        if (!config.getBoolean("vault-decay.enabled", false) || game.getState() == GameState.SETUP) return 1.0;
        long idleMillis = now - game.getTeam(golem.getTeam()).getLastDeliveryMillis();
        if (idleMillis < config.getLong("vault-decay.after-seconds", 120) * 1000L) return 1.0;
        return config.getDouble("vault-decay.aging-multiplier", 1.5);
    }

    private long rollDuration() {
        long base = game.getPlugin().settings().getLong("golems.aging-seconds", 210) * 1000L;
        long jitter = game.getPlugin().settings().getLong("golems.aging-jitter-seconds", 20) * 1000L;
        long jittered = base + (long) ((Math.random() * 2 - 1) * jitter);
        return Math.max(1000L, jittered);
    }
}
