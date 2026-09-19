package me.psikuvit.copperHeist.heist;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.task.LockpickTask;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enemy dock chests aren't free to open: a raider has to hold still next to
 * the chest while a lockpick timer runs (halved for Thieves), then gets a
 * short window to loot. A team's own dock chests open normally.
 */
public class DockLockManager {

    private final CopperHeist plugin;
    private final Game game;
    private final Map<UUID, Long> unlockedUntilMillis = new HashMap<>();
    private final Set<UUID> picking = new HashSet<>();

    public DockLockManager(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    public boolean isEnemyDock(Team playerTeam, Location chestLocation) {
        if (!game.feature("lockpicking")) return false;
        return game.getArena().site(playerTeam.opposite()).dockChests.contains(chestLocation);
    }

    public boolean isUnlocked(UUID player) {
        Long until = unlockedUntilMillis.get(player);
        return until != null && System.currentTimeMillis() < until;
    }

    public void begin(Player player, GamePlayer gp, Location chestLocation) {
        if (!picking.add(player.getUniqueId())) return;
        double seconds = game.settings().getDouble("dock.lockpick-seconds", 4.0)
                * game.getRoleService().lockpickMultiplier(gp.getRole());
        int totalTicks = Math.max(5, (int) (seconds * 20));
        new LockpickTask(plugin, game, this, player, chestLocation, totalTicks).runTaskTimer(plugin, 0L, 5L);
    }

    public void finish(Player player, Location chestLocation, boolean success) {
        picking.remove(player.getUniqueId());
        if (!success) return;
        long window = game.settings().getLong("dock.unlock-window-seconds", 15) * 1000L;
        unlockedUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + window);
        if (chestLocation.getBlock().getState() instanceof Chest chest) player.openInventory(chest.getInventory());
    }
}
