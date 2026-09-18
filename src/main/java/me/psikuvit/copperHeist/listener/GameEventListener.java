package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.AlarmDestroyedEvent;
import me.psikuvit.copperHeist.event.AlarmTriggeredEvent;
import me.psikuvit.copperHeist.event.LootDeliveredEvent;
import me.psikuvit.copperHeist.event.LootStolenEvent;
import me.psikuvit.copperHeist.event.MatchEndEvent;
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
        int total = game.getTeam(event.getTeam()).getScore();
        broadcast(game, Component.text(event.getTeam().displayName() + " delivered " + event.getValue()
                + " loot! (" + total + " total)", event.getTeam().color()));
    }

    @EventHandler
    public void onLootStolen(LootStolenEvent event) {
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

        Component title = winner != null
                ? Component.text(winner.displayName() + " WINS", winner.color())
                : Component.text("DRAW", NamedTextColor.YELLOW);
        Component subtitle = Component.text(event.getCopperScore() + " - " + event.getIronScore(), NamedTextColor.GRAY);
        for (Player player : event.getGame().onlinePlayers()) {
            player.showTitle(Title.title(title, subtitle));
        }
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
    }

    @EventHandler
    public void onVaultDrillDestroyed(VaultDrillDestroyedEvent event) {
        broadcast(event.getGame(), plugin.getMessageService().get("drill.destroyed", "player", event.getDestroyer().getName()));
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
