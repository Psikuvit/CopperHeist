package me.psikuvit.copperHeist.shop.action;

import org.bukkit.Bukkit;

import java.util.List;

/** Runs console commands (param {@code commands}, a list; {player} and {team} are substituted) - handy for custom rewards. */
public class CommandAction implements ShopAction {

    @Override
    public void perform(ShopPurchase purchase) {
        if (!(purchase.entry().params().get("commands") instanceof List<?> commands)) return;
        for (Object command : commands) {
            String line = String.valueOf(command)
                    .replace("{player}", purchase.player().getName())
                    .replace("{team}", purchase.gamePlayer().getTeam().name().toLowerCase());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
    }
}
