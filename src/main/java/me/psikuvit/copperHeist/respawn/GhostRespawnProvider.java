package me.psikuvit.copperHeist.respawn;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.task.RespawnTask;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Like spectator-wait, but the player stays in the world at their spawn as an invisible,
 * invulnerable ghost who can't pick anything up - so they can watch the fight without flying through walls.
 */
public class GhostRespawnProvider implements RespawnProvider {

    private final CopperHeist plugin;

    public GhostRespawnProvider(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public void begin(Game game, Player player, GamePlayer gamePlayer) {
        int delay = plugin.settings().getInt("match.respawn-delay-seconds", 6);
        if (delay <= 0) {
            game.finishRespawn(player, gamePlayer);
            return;
        }
        gamePlayer.setGhost(true);
        gamePlayer.protectFor(delay + 1);
        player.setGameMode(GameMode.ADVENTURE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, delay * 20 + 20, 0, false, false));
        var spawn = game.getArena().site(gamePlayer.getTeam()).spawn;
        if (spawn != null) player.teleport(spawn);
        new RespawnTask(plugin, game, player, gamePlayer, delay).runTaskTimer(plugin, 0L, 20L);
    }
}
