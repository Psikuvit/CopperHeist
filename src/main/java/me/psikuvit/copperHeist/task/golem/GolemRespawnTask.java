package me.psikuvit.copperHeist.task.golem;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.golem.GolemManager;
import org.bukkit.scheduler.BukkitRunnable;

/** Delayed replacement of a dead golem, if the team is still short. */
public class GolemRespawnTask extends BukkitRunnable {

    private final GolemManager golems;
    private final Team team;

    public GolemRespawnTask(GolemManager golems, Team team) {
        this.golems = golems;
        this.team = team;
    }

    @Override
    public void run() {
        golems.respawnIfShort(team);
    }
}
