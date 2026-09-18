package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.util.Cooldowns;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/** Launches players who step on a gust pad - upward, plus a forward push in the direction they're facing. */
public class GustPadListener implements Listener {

    private static final long COOLDOWN_SECONDS = 1;

    private final CopperHeist plugin;
    private final Cooldowns cooldowns = new Cooldowns();

    public GustPadListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null || game.getGamePlayer(player.getUniqueId()) == null) return;
        if (!cooldowns.isReady(player.getUniqueId())) return;

        for (Arena.GustPad pad : game.getArena().getGustPads()) {
            Location loc = pad.location();
            if (loc.getBlockX() == to.getBlockX() && loc.getBlockY() == to.getBlockY() && loc.getBlockZ() == to.getBlockZ()) {
                launch(player, pad.power());
                cooldowns.set(player.getUniqueId(), COOLDOWN_SECONDS);
                return;
            }
        }
    }

    private void launch(Player player, double power) {
        Vector push = player.getLocation().getDirection().setY(0);
        push = push.lengthSquared() < 0.0001 ? new Vector() : push.normalize().multiply(0.5 * power);
        player.setVelocity(push.setY(0.9 * power));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.4f);
    }
}
