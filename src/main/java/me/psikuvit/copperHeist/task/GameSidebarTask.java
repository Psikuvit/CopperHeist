package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.ui.SidebarService;
import org.bukkit.scheduler.BukkitRunnable;

/** Redraws a match's sidebar for everyone in it, for the whole life of the Game. */
public class GameSidebarTask extends BukkitRunnable {

    private final SidebarService sidebar;
    private final Game game;

    public GameSidebarTask(SidebarService sidebar, Game game) {
        this.sidebar = sidebar;
        this.game = game;
    }

    @Override
    public void run() {
        sidebar.update(game);
    }
}
