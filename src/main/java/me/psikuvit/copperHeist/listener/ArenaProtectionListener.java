package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

public class ArenaProtectionListener implements Listener {

    private final CopperHeist plugin;

    public ArenaProtectionListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getGameManager().getGame(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getGameManager().getGame(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getGameManager().getGame(player) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Chest chest)) return;

        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        Arena arena = game.getArena();
        var loc = chest.getLocation();
        boolean isVault = arena.site(me.psikuvit.copperHeist.game.Team.COPPER).vaultChests.contains(loc)
                || arena.site(me.psikuvit.copperHeist.game.Team.IRON).vaultChests.contains(loc);
        if (isVault) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Only golems can deliver loot into the vault.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        for (Arena arena : plugin.getArenaManager().all()) {
            if (arena.isInBounds(event.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
