package me.psikuvit.copperHeist.hook;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.Bukkit;

/**
 * Detects the optional plugins at startup and switches their integrations on. Each integration class is only
 * referenced from inside its own check, so a missing plugin jar never causes a NoClassDefFoundError.
 */
public final class HookManager {

    private HookManager() {
    }

    public static void enable(CopperHeist plugin) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(plugin).register();
            plugin.getLogger().info("PlaceholderAPI found - %copperheist_...% placeholders are available.");
        }
        Bukkit.getPluginManager().registerEvents(new Rewards(plugin), plugin);
    }
}
