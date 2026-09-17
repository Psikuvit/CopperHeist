package me.psikuvit.copperHeist.arena;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * MVP reset strategy: players can't break/place blocks in an arena, so there's
 * nothing to roll back structurally. Reset just clears entities and chests.
 */
public class ArenaResetter {

    public void reset(Arena arena) {
        clearEntities(arena);
        clearChests(arena);
    }

    public void clearEntities(Arena arena) {
        World world = arena.getWorld();
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (!arena.isInBounds(entity.getLocation())) continue;
            if (entity instanceof Item || entity instanceof Display) {
                entity.remove();
            } else if (entity.getType() == EntityType.COPPER_GOLEM && Pdc.has(entity, PdcKeys.MATCH_ID)) {
                // Only remove golems this plugin spawned - a real player-owned copper
                // golem that happened to wander into the bounds is left alone.
                entity.remove();
            }
        }
    }

    public void clearChests(Arena arena) {
        for (Team team : Team.values()) {
            Arena.TeamSite site = arena.site(team);
            clearAll(site.dockChests);
            clearAll(site.vaultChests);
        }
    }

    private void clearAll(List<Location> chests) {
        for (Location loc : chests) {
            if (loc == null || loc.getWorld() == null) continue;
            BlockState state = loc.getBlock().getState();
            if (state instanceof Chest chest) {
                Inventory inv = chest.getInventory();
                inv.clear();
            }
        }
    }
}
