package me.psikuvit.copperHeist.listener.loot;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.event.LootStolenEvent;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.task.PostRespawnTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class LootListener implements Listener {

    private final CopperHeist plugin;

    public LootListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (!LootItem.isLoot(item)) return;

        Game game = plugin.getGameManager().getGame(player);
        if (game == null || !game.isActive() || !game.getMatchId().equals(LootItem.getMatchId(item))) {
            event.setCancelled(true);
            return;
        }

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) {
            event.setCancelled(true);
            return;
        }

        if (gp.isGhost()) {
            event.setCancelled(true);
            return;
        }

        int carryLimit = game.settings().getInt("loot.carry-limit", 80);
        int itemValue = LootItem.getValue(item) * item.getAmount();
        if (!plugin.getLootWeightService().canCarry(player, item)) {
            event.setCancelled(true);
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "actionbar.carry-limit", "limit", carryLimit));
            return;
        }

        Team lastTeam = LootItem.getLastTeam(item);
        if (lastTeam != null && lastTeam != gp.getTeam()) {
            game.getTeam(gp.getTeam()).addSteal();
            Bukkit.getPluginManager().callEvent(new LootStolenEvent(game, player, lastTeam, itemValue));
        }
        LootItem.setLastTeam(item, gp.getTeam());
        LootItem.setLastCarrier(item, player.getUniqueId());
        game.getRoleService().breakInvisibility(player);
        if (LootItem.isRelic(item)) game.getRelicManager().onPickedUp(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        // The vanilla death line is dropped for everyone in a match; a death-message cosmetic tells its own line to the match instead.
        if (!game.settings().getBoolean("match.vanilla-death-messages", false)) event.deathMessage(null);

        Player killer = player.getKiller();
        GamePlayer victimGp = game.getGamePlayer(player.getUniqueId());
        if (victimGp != null && game.isActive()) victimGp.addDeath();
        if (killer == null && victimGp != null) {
            UUID recent = victimGp.recentAttacker(10);
            if (recent != null) killer = Bukkit.getPlayer(recent);
        }
        if (killer != null) {
            GamePlayer killerGp = game.getGamePlayer(killer.getUniqueId());
            if (killerGp != null && game.isActive()) killerGp.addKill();
            if (game.isActive()) {
                var cosmetics = plugin.getCosmetics();
                cosmetics.play(killer, CosmeticCategory.KILL_EFFECT, player.getLocation(), null, cosmetics.viewers(game));
                // The killer's own death message replaces the plain one, and is told to the match instead of the whole server.
                if (cosmetics.willPlay(killer, CosmeticCategory.DEATH_MESSAGE)) {
                    event.deathMessage(null);
                    cosmetics.play(killer, CosmeticCategory.DEATH_MESSAGE, player.getLocation(), player, cosmetics.viewers(game));
                }
            }
        }

        event.setKeepInventory(true);
        event.getDrops().clear();

        boolean lostRelic = false;
        List<ItemStack> dropped = new ArrayList<>();
        Iterator<ItemStack> it = player.getInventory().iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (item == null || !LootItem.isLoot(item)) continue;
            if (LootItem.isRelic(item)) {
                // Skipped rather than dropped - a lost relic should respawn
                // fresh, not sit as a ground item that could fall into the
                // exact void/lava that "lost" it in the first place.
                lostRelic = true;
            } else {
                dropped.add(item);
            }
            it.remove();
        }
        if (lostRelic) game.getRelicManager().onLost(player);
        if (game.isActive()) game.getLootBagManager().create(player.getLocation(), dropped);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        Arena.TeamSite site = game.getArena().site(gp.getTeam());
        if (site.spawn != null) event.setRespawnLocation(site.spawn);

        // Reapplied a tick late - giving items during the respawn event itself
        // can get clobbered by the client's own respawn handling. This is also
        // what makes a role change while dead take effect ("applies on respawn").
        new PostRespawnTask(plugin, game, player, gp).runTask(plugin);
    }
}
