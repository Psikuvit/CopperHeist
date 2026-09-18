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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Reacts to this plugin's own custom events (see the event package) with the
 * player-facing side effect - a broadcast, a title. Keeping this separate
 * from the managers that fire those events means GolemManager/RelicManager/
 * Game don't need to know how a moment gets announced, just that it happened.
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
            broadcast(game, plugin.getMessageService().get("relic.delivered", "team", event.getTeam().displayName()));
            return;
        }
        String totalSuffix = game.isScoreHidden() ? "" : " (" + game.getTeam(event.getTeam()).getScore() + " total)";
        broadcast(game, Component.text(event.getTeam().displayName() + " delivered " + event.getValue()
                + " loot!" + totalSuffix, event.getTeam().color()));
    }

    @EventHandler
    public void onLootStolen(LootStolenEvent event) {
        GamePlayer thief = event.getGame().getGamePlayer(event.getThief().getUniqueId());
        if (thief != null) {
            thief.addSteal();
            thief.addStolenValue(event.getValue());
        }
        broadcast(event.getGame(), Component.text(event.getThief().getName() + " stole loot from "
                + event.getVictimTeam().displayName() + "!", NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onRelicSpawn(RelicSpawnEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("relic.spawned"));
    }

    @EventHandler
    public void onRelicPickup(RelicPickupEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("relic.picked-up", "player", event.getPlayer().getName()));
    }

    @EventHandler
    public void onRelicLost(RelicLostEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("relic.lost"));
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        Team winner = event.getWinner();
        Component summary = winner != null
                ? Component.text("WINNER: " + winner.displayName().toUpperCase() + " TEAM ("
                        + event.getCopperScore() + " - " + event.getIronScore() + ")", winner.color())
                : Component.text("DRAW (" + event.getCopperScore() + " - " + event.getIronScore() + ")", NamedTextColor.YELLOW);
        broadcast(event.getGame(), summary);
        broadcastHighlights(event.getGame());
        if (winner != null) launchFireworks(event.getGame(), winner);

        Component title = winner != null
                ? Component.text(winner.displayName() + " WINS", winner.color())
                : Component.text("DRAW", NamedTextColor.YELLOW);
        Component subtitle = Component.text(event.getCopperScore() + " - " + event.getIronScore(), NamedTextColor.GRAY);
        for (Player player : event.getGame().onlinePlayers()) {
            player.showTitle(Title.title(title, subtitle));
        }
    }

    @EventHandler
    public void onPhaseChange(PhaseChangeEvent event) {
        String key = "phase." + event.getTo().name().toLowerCase().replace('_', '-');
        Component title = plugin.getMessageService().get(key + ".title");
        Component subtitle = plugin.getMessageService().get(key + ".subtitle");
        for (Player player : event.getGame().onlinePlayers()) {
            player.showTitle(Title.title(title, subtitle));
        }
        broadcast(event.getGame(), plugin.getMessageService().get(key + ".chat"));
    }

    @EventHandler
    public void onAlarmTriggered(AlarmTriggeredEvent event) {
        broadcastTeam(event.getGame(), event.getAlarm().getTeam(), plugin.getMessageService().get("alarm.triggered"));
    }

    @EventHandler
    public void onAlarmDestroyed(AlarmDestroyedEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("alarm.destroyed", "player", event.getDestroyer().getName()));
    }

    @EventHandler
    public void onVaultDrillPlaced(VaultDrillPlacedEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("drill.placed",
                "team", event.getDrill().getAttacker().displayName(), "target", event.getDrill().getDefender().displayName()));
    }

    @EventHandler
    public void onVaultDrillCompleted(VaultDrillCompletedEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("drill.completed",
                "target", event.getDrill().getDefender().displayName(), "seconds", event.getBreachSeconds()));
        broadcastTeamTitle(event.getGame(), event.getDrill().getDefender(),
                plugin.getMessageService().get("drill.breached.title"), plugin.getMessageService().get("drill.breached.subtitle"));
    }

    @EventHandler
    public void onVaultDrillDestroyed(VaultDrillDestroyedEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("drill.destroyed", "player", event.getDestroyer().getName()));
    }

    private void broadcastTeamTitle(Game game, Team team, Component title, Component subtitle) {
        for (Player player : game.onlinePlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp != null && gp.getTeam() == team) player.showTitle(Title.title(title, subtitle));
        }
    }

    private void launchFireworks(Game game, Team winner) {
        Color color = winner == Team.COPPER ? Color.ORANGE : Color.SILVER;
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
        if (mvp != null) {
            broadcast(game, plugin.getMessageService().get("summary.mvp",
                    "player", nameOf(mvp), "delivered", mvp.getDelivered()));
        }
        if (topThief != null) {
            broadcast(game, plugin.getMessageService().get("summary.top-thief",
                    "player", nameOf(topThief), "count", topThief.getSteals(), "value", topThief.getStolenValue()));
        }
        if (topMechanic != null) {
            broadcast(game, plugin.getMessageService().get("summary.best-mechanic",
                    "player", nameOf(topMechanic), "count", topMechanic.getScrapes()));
        }
        if (topKiller != null) {
            broadcast(game, plugin.getMessageService().get("summary.most-kills",
                    "player", nameOf(topKiller), "count", topKiller.getKills()));
        }
    }

    private String nameOf(GamePlayer gp) {
        Player player = Bukkit.getPlayer(gp.getUuid());
        return player != null ? player.getName() : "?";
    }

    private void broadcast(Game game, Component message) {
        for (Player player : game.onlinePlayers()) player.sendMessage(message);
    }

    private void broadcastTeam(Game game, Team team, Component message) {
        for (Player player : game.onlinePlayers()) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp != null && gp.getTeam() == team) player.sendMessage(message);
        }
    }
}
