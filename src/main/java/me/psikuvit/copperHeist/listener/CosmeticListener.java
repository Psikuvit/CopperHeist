package me.psikuvit.copperHeist.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticService;
import me.psikuvit.copperHeist.event.LevelUpEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The gameplay hooks of the cosmetics that need one: projectile trails, keeping cosmetic fireworks harmless, and the level and title in
 * chat. Kill, victory, golem, NPC and join effects are triggered from where those things already happen.
 */
public class CosmeticListener implements Listener {

    private final CopperHeist plugin;

    public CosmeticListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    /** Arrows, wind charges, tridents, thrown potions: whatever a player in a running match launches gets their trail. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) return;
        CosmeticService cosmetics = plugin.getCosmetics();
        if (!cosmetics.enabled()) return;
        Game game = plugin.getGameManager().getGame(shooter);
        if (game == null || !game.isActive()) return;
        cosmetics.play(shooter, CosmeticCategory.TRAIL, projectile.getLocation(), projectile, cosmetics.viewers(game));
    }

    /** A cosmetic firework explodes for show only - it must never hurt anyone, whoever is standing next to it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework && Pdc.has(firework, PdcKeys.COSMETIC)) event.setCancelled(true);
    }

    @EventHandler
    public void onLevelUp(LevelUpEvent event) {
        plugin.getCosmetics().refreshPrefix(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getCosmetics().forget(event.getPlayer().getUniqueId());
    }

    /**
     * Puts the player's level and title in front of their name (chat.enabled in config.yml, off by default because other chat plugins
     * also format chat). Runs off the main thread, so it only reads the prefix that was prepared on the main thread.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.settings().getBoolean("chat.enabled", false)) return;
        Component prefix = plugin.getCosmetics().prefix(event.getPlayer().getUniqueId());
        if (prefix.equals(Component.empty())) return;
        event.renderer(ChatRenderer.viewerUnaware((source, name, message) -> prefix.append(name).append(Component.text(": ")).append(message)));
    }
}
