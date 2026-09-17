package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
            player.sendActionBar(Component.text("Carrying too much loot! (limit " + carryLimit + ")", NamedTextColor.RED));
            return;
        }

        Team lastTeam = LootItem.getLastTeam(item);
        if (lastTeam != null && lastTeam != gp.getTeam()) {
            game.getTeam(gp.getTeam()).addSteal();
            Component msg = Component.text(player.getName() + " stole loot from " + lastTeam.displayName() + "!", NamedTextColor.YELLOW);
            for (Player online : game.onlinePlayers()) online.sendMessage(msg);
        }
        LootItem.setLastTeam(item, gp.getTeam());
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
}
