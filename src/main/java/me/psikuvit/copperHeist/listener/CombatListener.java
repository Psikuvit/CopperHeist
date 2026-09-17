package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.golem.HeistGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class CombatListener implements Listener {

    private final CopperHeist plugin;

    public CombatListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();

        if (victim.getType() == EntityType.COPPER_GOLEM) {
            onGolemHit(event, victim);
            return;
        }

        if (victim instanceof Player victimPlayer && resolveAttacker(event.getDamager()) instanceof Player attacker) {
            Game game = plugin.getGameManager().getGame(victimPlayer);
            if (game == null) return;
            GamePlayer victimGp = game.getGamePlayer(victimPlayer.getUniqueId());
            GamePlayer attackerGp = game.getGamePlayer(attacker.getUniqueId());
            if (victimGp != null && attackerGp != null && victimGp.getTeam() == attackerGp.getTeam()) {
                event.setCancelled(true);
            }
        }
    }

    private void onGolemHit(EntityDamageByEntityEvent event, Entity victim) {
        HeistGolem golem = plugin.getGameManager().getGolem(victim.getUniqueId());
        if (golem == null) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return;
        Game game = plugin.getGameManager().getGameForGolem(victim.getUniqueId());
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(attacker.getUniqueId());
        if (gp == null || gp.getTeam() == golem.getTeam()) {
            event.setCancelled(true);
            return;
        }
        game.getGolemManager().onDamaged(golem, attacker);
    }

    @EventHandler
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        if (!(event.getEntity() instanceof Player player)) return;
        Game game = plugin.getGameManager().getGame(player);
        if (game == null || !game.isActive()) return;
        event.setDamage(1000);
    }

    @EventHandler
    public void onGolemDeath(EntityDeathEvent event) {
        if (event.getEntity().getType() != EntityType.COPPER_GOLEM) return;
        HeistGolem golem = plugin.getGameManager().getGolem(event.getEntity().getUniqueId());
        if (golem == null) return;
        event.getDrops().clear();
        Game game = plugin.getGameManager().getGameForGolem(event.getEntity().getUniqueId());
        if (game != null) game.getGolemManager().onDeath(golem);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
