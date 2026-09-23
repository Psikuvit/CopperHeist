package me.psikuvit.copperHeist.listener.heist;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.util.Cooldowns;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/** Launches players who step on a gust pad - upward, plus a forward push in the direction they're facing. */
public class GustPadListener implements Listener {

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
                cooldowns.set(player.getUniqueId(), plugin.settings().getLong("gust-pads.cooldown-seconds", 1));
                return;
            }
        }
    }

    private void launch(Player player, double power) {
        double push = plugin.settings().getDouble("gust-pads.push-multiplier", 0.5);
        double lift = plugin.settings().getDouble("gust-pads.lift-multiplier", 0.9);
        Vector direction = player.getLocation().getDirection().setY(0);
        direction = direction.lengthSquared() < 0.0001 ? new Vector() : direction.normalize().multiply(push * power);
        player.setVelocity(direction.setY(lift * power));

        NamespacedKey soundKey = NamespacedKey.fromString(plugin.settings().getString("gust-pads.sound", "entity.ender_dragon.flap"));
        Sound sound = soundKey == null ? null : Registry.SOUNDS.get(soundKey);
        if (sound != null) player.getWorld().playSound(player.getLocation(), sound, 1.0f, 1.4f);
    }
}
