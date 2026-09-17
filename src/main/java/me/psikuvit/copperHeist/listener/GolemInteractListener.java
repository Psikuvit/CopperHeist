package me.psikuvit.copperHeist.listener;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.golem.HeistGolem;
import me.psikuvit.copperHeist.loot.LootItem;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().name().endsWith("_AXE")) {
            handleScrape(player, golem, game);
            return;
        }

        if (golem.getEntity().getWeatheringState() == WeatheringCopperState.OXIDIZED && golem.isCarrying()) {
            handleTakeStack(player, golem, game);
        }
    }

    private void handleScrape(Player player, HeistGolem golem, Game game) {
        WeatheringCopperState before = golem.getEntity().getWeatheringState();
        if (before == WeatheringCopperState.UNAFFECTED) {
            player.sendActionBar(plugin.getMessageService().get("actionbar.golem-already-fresh"));
            return;
        }
        boolean scraped = game.getGolemManager().scrape(golem);
        if (scraped) {
            player.sendActionBar(plugin.getMessageService().get("actionbar.golem-scraped",
                    "before", before.name(), "after", golem.getEntity().getWeatheringState().name()));
        } else {
            long remaining = game.getGolemManager().scrapeCooldownRemaining(golem.getEntity().getUniqueId());
            player.sendActionBar(plugin.getMessageService().get("actionbar.scrape-cooldown", "seconds", remaining));
        }
    }

    private void handleTakeStack(Player player, HeistGolem golem, Game game) {
        ItemStack carried = golem.getCarried();
        if (carried == null) return;
        int carryLimit = plugin.getConfig().getInt("loot.carry-limit", 80);
        int currentValue = plugin.getLootWeightService().getCarriedValue(player);
        int itemValue = LootItem.getValue(carried) * carried.getAmount();
        if (currentValue + itemValue > carryLimit) {
            player.sendActionBar(plugin.getMessageService().get("actionbar.golem-carry-limit"));
            return;
        }
        LootItem.setLastTeam(carried, golem.getTeam());
        player.getInventory().addItem(carried);
        golem.setCarried(null);
        game.getGolemManager().updateLabel(golem);
        player.sendActionBar(plugin.getMessageService().get("actionbar.golem-stack-taken"));
    }
}
