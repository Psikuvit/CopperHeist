package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
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
        Team vaultTeam = arena.site(Team.COPPER).vaultChests.contains(loc) ? Team.COPPER
                : arena.site(Team.IRON).vaultChests.contains(loc) ? Team.IRON : null;
        if (vaultTeam == null) return;

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        boolean breachingAttacker = gp != null && gp.getTeam() != vaultTeam && game.getVaultDrillManager().isBreached(vaultTeam);
        if (!breachingAttacker) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Only golems can deliver loot into the vault.", NamedTextColor.RED));
        }
    }

    /** The relic can't be stashed in an ender chest - it has to stay in play. */
    @EventHandler
    public void onEnderChestClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        boolean movingRelic = LootItem.isRelic(event.getCursor())
                || (event.isShiftClick() && LootItem.isRelic(event.getCurrentItem())
                    && event.getClickedInventory() != event.getInventory());
        if (movingRelic) event.setCancelled(true);
    }

    /** The relic can't be deliberately dropped - carry it to a dock like any other loot. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (LootItem.isRelic(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    /** Hoppers, droppers and minecarts can't shuffle items in or out of anything inside an arena. */
    @EventHandler
    public void onItemMove(InventoryMoveItemEvent event) {
        for (var inventory : new Inventory[]{event.getSource(), event.getDestination()}) {
            var loc = inventory.getLocation();
            if (loc == null) continue;
            for (Arena arena : plugin.getArenaManager().all()) {
                if (arena.isInBounds(loc)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPearl(ProjectileLaunchEvent event) {
        if (event.getEntityType() != EntityType.ENDER_PEARL) return;
        if (event.getEntity().getShooter() instanceof Player player && plugin.getGameManager().getGame(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getGameManager().getGame(player) != null) {
            event.setCancelled(true);
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
