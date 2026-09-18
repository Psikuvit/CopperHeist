package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class LootListener implements Listener {

    private final CopperHeist plugin;

    public LootListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (!LootItem.isLoot(item)) return;

        Game game = plugin.getGameManager().getGame(player);
        if (game == null || !game.isActive() || !game.getMatchId().equals(LootItem.getMatchId(item))) {
            event.setCancelled(true);
            return;
        }

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) {
            event.setCancelled(true);
            return;
        }

        int carryLimit = plugin.getConfig().getInt("loot.carry-limit", 80);
        int currentValue = plugin.getLootWeightService().getCarriedValue(player);
        int itemValue = LootItem.getValue(item) * item.getAmount();
        if (currentValue + itemValue > carryLimit) {
            event.setCancelled(true);
            player.sendActionBar(plugin.getMessageService().get("actionbar.carry-limit", "limit", carryLimit));
            return;
        }

        Team lastTeam = LootItem.getLastTeam(item);
        if (lastTeam != null && lastTeam != gp.getTeam()) {
            game.getTeam(gp.getTeam()).addSteal();
            Component msg = Component.text(player.getName() + " stole loot from " + lastTeam.displayName() + "!", NamedTextColor.YELLOW);
            for (Player online : game.onlinePlayers()) online.sendMessage(msg);
        }
        LootItem.setLastTeam(item, gp.getTeam());
        game.getRoleService().breakInvisibility(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        event.setKeepInventory(true);
        event.getDrops().clear();

        Iterator<ItemStack> it = player.getInventory().iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (item != null && LootItem.isLoot(item)) {
                event.getDrops().add(item);
                it.remove();
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        Arena.TeamSite site = game.getArena().site(gp.getTeam());
        if (site.spawn != null) event.setRespawnLocation(site.spawn);

        // Reapplied a tick late - giving items during the respawn event itself
        // can get clobbered by the client's own respawn handling. This is also
        // what makes a role change while dead take effect (doc §8: "applies on respawn").
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.getGameManager().getGame(player) == game) {
                game.getRoleService().giveLoadout(player, gp.getRole(), gp.getTeam());
            }
        });
    }
}
