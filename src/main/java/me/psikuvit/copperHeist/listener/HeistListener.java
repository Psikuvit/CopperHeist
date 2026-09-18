package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.heist.Alarm;
import me.psikuvit.copperHeist.heist.VaultDrill;
import me.psikuvit.copperHeist.shop.ShopItem;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Placement (Alarm, Vault Drill) and hitbox-hit handling for the section 6 stealing/sabotage tools. */
public class HeistListener implements Listener {

    private final CopperHeist plugin;

    public HeistListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String shopKey = Pdc.get(item, PdcKeys.SHOP_ITEM);
        if (shopKey == null) return;

        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        if (ShopItem.ALARM.key.equals(shopKey)) {
            event.setCancelled(true);
            placeAlarm(player, game, gp, event.getClickedBlock().getLocation(), item);
        } else if (ShopItem.VAULT_DRILL.key.equals(shopKey)) {
            event.setCancelled(true);
            placeDrill(player, game, gp, event.getClickedBlock().getLocation(), item);
        }
    }

    private void placeAlarm(Player player, Game game, GamePlayer gp, Location loc, ItemStack item) {
        var alarmManager = game.getAlarmManager();
        Team team = gp.getTeam();
        if (!alarmManager.isWithinPlacementRange(team, loc)) {
            player.sendActionBar(plugin.getMessageService().get("alarm.too-far"));
            return;
        }
        Alarm alarm = alarmManager.place(team, loc);
        if (alarm == null) {
            player.sendActionBar(plugin.getMessageService().get("alarm.cap-reached"));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.sendActionBar(plugin.getMessageService().get("alarm.placed",
                "count", alarmManager.countFor(team), "cap", alarmManager.capFor(team)));
    }

    private void placeDrill(Player player, Game game, GamePlayer gp, Location loc, ItemStack item) {
        Team attackerTeam = gp.getTeam();
        Team defenderTeam = attackerTeam.opposite();
        Arena.TeamSite defenderSite = game.getArena().site(defenderTeam);
        if (defenderSite.vaultDoor == null || !defenderSite.vaultDoor.equals(loc)) return;

        long cooldown = game.getVaultDrillManager().cooldownRemainingSeconds(attackerTeam);
        if (cooldown > 0) {
            player.sendActionBar(plugin.getMessageService().get("drill.on-cooldown", "seconds", cooldown));
            return;
        }
        if (game.getVaultDrillManager().isActive(defenderTeam)) {
            player.sendActionBar(plugin.getMessageService().get("drill.already-active"));
            return;
        }

        VaultDrill drill = game.getVaultDrillManager().place(attackerTeam);
        if (drill == null) return;
        item.setAmount(item.getAmount() - 1);
    }

    @EventHandler
    public void onHitboxDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (victim.getType() != EntityType.INTERACTION) return;
        event.setCancelled(true);

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return;
        Game game = plugin.getGameManager().getGameForHeistEntity(victim.getUniqueId());
        if (game == null) return;

        Alarm alarm = game.getAlarmManager().findByEntity(victim.getUniqueId());
        if (alarm != null) {
            GamePlayer gp = game.getGamePlayer(attacker.getUniqueId());
            if (gp != null && gp.getTeam() != alarm.getTeam()) game.getAlarmManager().destroy(alarm, attacker);
            return;
        }

        VaultDrill drill = game.getVaultDrillManager().findByEntity(victim.getUniqueId());
        if (drill != null) game.getVaultDrillManager().onHit(drill, attacker);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
