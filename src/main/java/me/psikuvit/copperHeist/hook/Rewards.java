package me.psikuvit.copperHeist.hook;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.logging.Level;

/**
 * Pays players when a match ends (rewards.provider: none | vault | commands). The winning team gets "win", the
 * losing team "loss" (a draw pays neither), and the MVP gets "mvp" on top. Vault pays money; commands runs
 * console commands with {player} and {uuid} filled in, so any economy or crate plugin can be hooked.
 */
public class Rewards implements Listener {

    private final CopperHeist plugin;
    private VaultEconomy vault;

    public Rewards(CopperHeist plugin) {
        this.plugin = plugin;
    }

    /** Looked up on first use: the economy plugin usually registers with Vault after this plugin has enabled. */
    private VaultEconomy vault() {
        if (vault == null && Bukkit.getPluginManager().isPluginEnabled("Vault")) vault = VaultEconomy.hook();
        if (vault == null) {
            plugin.getLogger().warning("rewards.provider is vault but Vault (with an economy plugin) isn't available - no rewards paid.");
        }
        return vault;
    }

    private String provider() {
        return plugin.settings().getString("rewards.provider", "none");
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        String provider = provider();
        if ("none".equalsIgnoreCase(provider)) return;
        Game game = event.getGame();
        Team winner = event.getWinner();
        GamePlayer mvp = game.mvp();

        for (GamePlayer gp : game.gamePlayers()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(gp.getUuid());
            if (winner != null) pay(provider, player, gp.getTeam() == winner ? "win" : "loss");
            if (gp == mvp) pay(provider, player, "mvp");
        }
    }

    private void pay(String provider, OfflinePlayer player, String reason) {
        if ("vault".equalsIgnoreCase(provider)) {
            double amount = plugin.settings().getDouble("rewards.vault." + reason, 0);
            VaultEconomy economy = amount > 0 ? vault() : null;
            if (economy != null) economy.deposit(player, amount);
        } else if ("commands".equalsIgnoreCase(provider)) {
            List<String> commands = plugin.settings().getStringList("rewards.commands." + reason);
            for (String command : commands) {
                String filled = command.replace("{player}", String.valueOf(player.getName()))
                        .replace("{uuid}", player.getUniqueId().toString());
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), filled);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "Reward command failed: " + filled, ex);
                }
            }
        }
    }
}
