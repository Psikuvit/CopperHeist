package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/** Releases a disconnected player's slot once the rejoin grace period runs out. */
public class RejoinExpiryTask extends BukkitRunnable {

    private final Game game;
    private final UUID player;

    public RejoinExpiryTask(Game game, UUID player) {
        this.game = game;
        this.player = player;
    }

    @Override
    public void run() {
        game.expireDisconnected(player);
    }
}
