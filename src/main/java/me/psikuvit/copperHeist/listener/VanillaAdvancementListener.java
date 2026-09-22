package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps Minecraft's own advancements out of the way so the plugin's achievements are the only ones players see:
 * <ul>
 * <li>{@code advancements.hide-chat}: no "X has made the advancement Y" in chat (the announcement is removed, and the announce_advancements
 *     game rule is turned off in every world);</li>
 * <li>{@code advancements.remove-vanilla}: the vanilla advancements are removed from the server (recipes are kept), so their toasts never
 *     appear and the advancements screen is empty. Datapack reloads bring them back, so this runs again after every server load.</li>
 * </ul>
 */
public class VanillaAdvancementListener implements Listener {

    private final CopperHeist plugin;

    public VanillaAdvancementListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (plugin.settings().getBoolean("advancements.hide-chat", true)) event.message(null);
    }

    /** Fires once every plugin and world is up (and again after a datapack reload), which is when the vanilla advancements exist. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        apply();
    }

    /** Applies the settings now; called on server load and once at startup for a plugin that is enabled after the server finished loading. */
    public void apply() {
        if (plugin.settings().getBoolean("advancements.hide-chat", true)) {
            for (World world : Bukkit.getWorlds()) world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        }
        if (plugin.settings().getBoolean("advancements.remove-vanilla", true)) removeVanilla();
    }

    @SuppressWarnings("deprecation") // removing advancements is "unsafe" API in Paper; it is what makes the toasts and screen go away
    private void removeVanilla() {
        List<NamespacedKey> vanilla = new ArrayList<>();
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            NamespacedKey key = it.next().getKey();
            if (isVanillaAdvancement(key.getNamespace(), key.getKey())) vanilla.add(key);
        }
        int removed = 0;
        for (NamespacedKey key : vanilla) {
            if (Bukkit.getUnsafe().removeAdvancement(key)) removed++;
        }
        if (removed > 0) plugin.getLogger().info("Removed " + removed + " vanilla advancement(s) (advancements.remove-vanilla).");
    }

    /** Vanilla advancements are the "minecraft" ones; recipe unlocks are kept, because removing them breaks the recipe book. */
    static boolean isVanillaAdvancement(String namespace, String path) {
        return namespace.equals(NamespacedKey.MINECRAFT) && !path.startsWith("recipes/");
    }
}
