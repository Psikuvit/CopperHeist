package me.psikuvit.copperHeist.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** The only class that touches Vault's API - it is only loaded once Vault is known to be installed. */
final class VaultEconomy {

    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    static VaultEconomy hook() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new VaultEconomy(registration.getProvider());
    }

    void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }
}
