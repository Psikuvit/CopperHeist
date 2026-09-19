package me.psikuvit.copperHeist.listener;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.golem.HeistGolem;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.shop.ItemUse;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class GolemInteractListener implements Listener {

    private final CopperHeist plugin;

    public GolemInteractListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.COPPER_GOLEM) return;
        HeistGolem golem = plugin.getGameManager().getGolem(event.getRightClicked().getUniqueId());
        Game game = plugin.getGameManager().getGameForGolem(event.getRightClicked().getUniqueId());
        if (golem == null || game == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp != null && gp.getTeam() == golem.getTeam() && golem.isStunned()
                && game.getRoleService().canClearStun(gp)) {
            golem.clearStun();
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.stun-cleared"));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        String shopKey = Pdc.get(hand, PdcKeys.SHOP_ITEM);
        if (ItemUse.HONEYCOMB.equals(shopKey)) {
            handleWax(player, golem, game, hand);
            return;
        }
        if (hand.getType().name().endsWith("_AXE")) {
            handleScrape(player, golem, game, gp);
            return;
        }

        if (golem.getEntity().getWeatheringState() == WeatheringCopperState.OXIDIZED && golem.isCarrying()) {
            handleTakeStack(player, golem, game);
        }
    }

    /** Storm Rod isn't golem-targeted - right-click anywhere while holding it. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        if (!ItemUse.STORM_ROD.equals(Pdc.get(item, PdcKeys.SHOP_ITEM))) return;

        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        event.setCancelled(true);
        game.getGolemManager().stormReset(gp.getTeam());
        plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.storm-rod-used"));
        item.setAmount(item.getAmount() - 1);
    }

    private void handleWax(Player player, HeistGolem golem, Game game, ItemStack honeycomb) {
        game.getGolemManager().wax(golem);
        honeycomb.setAmount(honeycomb.getAmount() - 1);
        plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.golem-waxed"));
    }

    private void handleScrape(Player player, HeistGolem golem, Game game, GamePlayer gp) {
        WeatheringCopperState before = golem.getEntity().getWeatheringState();
        if (golem.isWaxed()) {
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.golem-already-fresh"));
            return;
        }
        if (before == WeatheringCopperState.UNAFFECTED) {
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.golem-already-fresh"));
            return;
        }
        double multiplier = gp == null ? 1.0 : game.getRoleService().scrapeCooldownMultiplier(gp.getRole());
        boolean scraped = game.getGolemManager().scrape(golem, multiplier);
        if (scraped) {
            if (gp != null) gp.addScrape();
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.golem-scraped",
                    "before", before.name(), "after", golem.getEntity().getWeatheringState().name()));
        } else {
            long remaining = game.getGolemManager().scrapeCooldownRemaining(golem.getEntity().getUniqueId());
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.scrape-cooldown", "seconds", remaining));
        }
    }

    private void handleTakeStack(Player player, HeistGolem golem, Game game) {
        ItemStack carried = golem.getCarried();
        if (carried == null) return;
        int carryLimit = plugin.settings().getInt("loot.carry-limit", 80);
        int currentValue = plugin.getLootWeightService().getCarriedValue(player);
        int itemValue = LootItem.getValue(carried) * carried.getAmount();
        if (currentValue + itemValue > carryLimit) {
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.golem-carry-limit"));
            return;
        }
        LootItem.setLastTeam(carried, golem.getTeam());
        LootItem.setLastCarrier(carried, player.getUniqueId());
        player.getInventory().addItem(carried);
        golem.setCarried(null);
        game.getGolemManager().updateLabel(golem);
        plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.golem-stack-taken"));
    }
}
