package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.AlarmDestroyedEvent;
import me.psikuvit.copperHeist.event.AlarmTriggeredEvent;
import me.psikuvit.copperHeist.event.LootDeliveredEvent;
import me.psikuvit.copperHeist.event.LootStolenEvent;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.event.PhaseChangeEvent;
import me.psikuvit.copperHeist.event.RelicLostEvent;
import me.psikuvit.copperHeist.event.RelicPickupEvent;
import me.psikuvit.copperHeist.event.RelicSpawnEvent;
import me.psikuvit.copperHeist.event.VaultDrillCompletedEvent;
import me.psikuvit.copperHeist.event.VaultDrillDestroyedEvent;
import me.psikuvit.copperHeist.event.VaultDrillPlacedEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.Locale;

/**
 * Reacts to this plugin's own custom events (see the event package) with the
 * player-facing side effect - a broadcast, a title. Keeping this separate
 * from the managers that fire those events means GolemManager/RelicManager/
 * Game don't need to know how a moment gets announced, just that it happened.
 * Every line is a lang key, so each player reads it in their own language.
 */
public class GameEventListener implements Listener {

    private final CopperHeist plugin;

    public GameEventListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLootDelivered(LootDeliveredEvent event) {
        Game game = event.getGame();
        if (event.isRelic()) {
            broadcast(game, "relic.delivered", "team", event.getTeam().displayName(), "value", event.getValue());
            return;
        }
        Team team = event.getTeam();
        var messages = plugin.getMessageService();
        for (Player player : game.onlinePlayers()) {
            String total = game.isScoreHidden() ? ""
                    : messages.rawFor(player, "event.total-suffix", "total", game.getTeam(team).getScore());
            player.sendMessage(messages.get(player, "event.loot-delivered", "color", colorName(team),
                    "team", team.displayName(), "value", event.getValue(), "total", total));
        }
    }

    @EventHandler
    public void onLootStolen(LootStolenEvent event) {
        GamePlayer thief = event.getGame().getGamePlayer(event.getThief().getUniqueId());
        if (thief != null) {
            thief.addSteal();
            thief.addStolenValue(event.getValue());
        }
        broadcast(event.getGame(), "event.loot-stolen", "player", event.getThief().getName(),
                "victim", event.getVictimTeam().displayName());
    }

    @EventHandler
    public void onRelicSpawn(RelicSpawnEvent event) {
        broadcast(event.getGame(), "relic.spawned");
    }

    @EventHandler
    public void onRelicPickup(RelicPickupEvent event) {
        broadcast(event.getGame(), "relic.picked-up", "player", event.getPlayer().getName());
    }

    @EventHandler
    public void onRelicLost(RelicLostEvent event) {
        broadcast(event.getGame(), "relic.lost");
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        Team winner = event.getWinner();
        Game game = event.getGame();
        var messages = plugin.getMessageService();
        for (Player player : game.onlinePlayers()) {
            if (winner != null) {
                player.sendMessage(messages.get(player, "match.winner", "color", colorName(winner),
                        "team", winner.displayName().toUpperCase(Locale.ROOT),
                        "copper", event.getCopperScore(), "iron", event.getIronScore()));
                player.showTitle(Title.title(
                        messages.get(player, "match.title-win", "color", colorName(winner), "team", winner.displayName()),
                        messages.get(player, "match.subtitle", "copper", event.getCopperScore(), "iron", event.getIronScore())));
            } else {
                player.sendMessage(messages.get(player, "match.draw", "copper", event.getCopperScore(), "iron", event.getIronScore()));
                player.showTitle(Title.title(messages.get(player, "match.title-draw"),
                        messages.get(player, "match.subtitle", "copper", event.getCopperScore(), "iron", event.getIronScore())));
            }
        }
        broadcastHighlights(game);
        if (winner != null) launchFireworks(game, winner);
    }

    @EventHandler
    public void onPhaseChange(PhaseChangeEvent event) {
        String key = "phase." + event.getTo().name().toLowerCase(Locale.ROOT).replace('_', '-');
        var messages = plugin.getMessageService();
        for (Player player : event.getGame().onlinePlayers()) {
            player.showTitle(Title.title(messages.get(player, key + ".title"), messages.get(player, key + ".subtitle")));
            player.sendMessage(messages.get(player, key + ".chat"));
        }
    }

    @EventHandler
    public void onAlarmTriggered(AlarmTriggeredEvent event) {
        broadcastTeam(event.getGame(), event.getAlarm().team(), "alarm.triggered");
    }

    @EventHandler
    public void onAlarmDestroyed(AlarmDestroyedEvent event) {
        broadcast(event.getGame(), "alarm.destroyed", "player", event.getDestroyer().getName());
    }

    @EventHandler
    public void onVaultDrillPlaced(VaultDrillPlacedEvent event) {
        broadcast(event.getGame(), "drill.placed", "team", event.getDrill().getAttacker().displayName(),
                "target", event.getDrill().getDefender().displayName());
    }

    @EventHandler
    public void onVaultDrillCompleted(VaultDrillCompletedEvent event) {
        broadcast(event.getGame(), "drill.completed", "target", event.getDrill().getDefender().displayName(),
                "seconds", event.getBreachSeconds());
        broadcastTeamTitle(event.getGame(), event.getDrill().getDefender(), "drill.breached.subtitle");
    }

    @EventHandler
    public void onVaultDrillDestroyed(VaultDrillDestroyedEvent event) {
        broadcast(event.getGame(), "drill.destroyed", "player", event.getDestroyer().getName());
    }

    // ---- helpers ----

    private String colorName(Team team) {
        // "copper" / "iron" are theme tags, so the team's announcements use the configured team colors.
        return team.name().toLowerCase(Locale.ROOT);
    }

    private void broadcast(Game game, String key, Object... placeholders) {
        for (Player player : game.onlinePlayers()) player.sendMessage(plugin.getMessageService().get(player, key, placeholders));
    }

    private void broadcastTeam(Game game, Team team, String key, Object... placeholders) {
        for (Player player : game.onlinePlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp != null && gp.getTeam() == team) player.sendMessage(plugin.getMessageService().get(player, key, placeholders));
        }
    }

    private void broadcastTeamTitle(Game game, Team team, String subtitleKey) {
        var messages = plugin.getMessageService();
        for (Player player : game.onlinePlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp == null || gp.getTeam() != team) continue;
            player.showTitle(Title.title(messages.get(player, "drill.breached.title"), messages.get(player, subtitleKey)));
        }
    }

    private void launchFireworks(Game game, Team winner) {
        Color color = winner.armorColor();
        for (Player player : game.onlinePlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp == null || gp.getTeam() != winner) continue;
            player.getWorld().spawn(player.getLocation(), Firework.class, firework -> {
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().withColor(color).withFade(Color.WHITE)
                        .with(FireworkEffect.Type.BALL_LARGE).build());
                meta.setPower(1);
                firework.setFireworkMeta(meta);
            });
        }
    }

    private void broadcastHighlights(Game game) {
        GamePlayer topThief = null;
        GamePlayer topMechanic = null;
        GamePlayer topKiller = null;
        GamePlayer mvp = null;
        for (Player player : game.onlinePlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp == null) continue;
            if (gp.getSteals() > 0 && (topThief == null || gp.getSteals() > topThief.getSteals())) topThief = gp;
            if (gp.getScrapes() > 0 && (topMechanic == null || gp.getScrapes() > topMechanic.getScrapes())) topMechanic = gp;
            if (gp.mvpScore() > 0 && (mvp == null || gp.mvpScore() > mvp.mvpScore())) mvp = gp;
            if (gp.getKills() > 0 && (topKiller == null || gp.getKills() > topKiller.getKills())) topKiller = gp;
        }
        if (mvp != null) broadcast(game, "summary.mvp", "player", nameOf(mvp), "delivered", mvp.getDelivered());
        if (topThief != null) {
            broadcast(game, "summary.top-thief", "player", nameOf(topThief), "count", topThief.getSteals(),
                    "value", topThief.getStolenValue());
        }
        if (topMechanic != null) {
            broadcast(game, "summary.best-mechanic", "player", nameOf(topMechanic), "count", topMechanic.getScrapes());
        }
        if (topKiller != null) broadcast(game, "summary.most-kills", "player", nameOf(topKiller), "count", topKiller.getKills());
    }

    private String nameOf(GamePlayer gp) {
        Player player = Bukkit.getPlayer(gp.getUuid());
        return player != null ? player.getName() : "?";
    }
}
