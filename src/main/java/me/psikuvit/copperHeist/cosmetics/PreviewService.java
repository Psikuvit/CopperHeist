package me.psikuvit.copperHeist.cosmetics;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.npc.NpcHandle;
import me.psikuvit.copperHeist.npc.NpcSpawner;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a shop keeper look to one player, cinematically: the keeper is spawned on the preview stage (/ch setpreview) and hidden from
 * everyone else, and the player is put in spectator mode watching an invisible camera in front of it - so they can't move, turn or be
 * seen. It ends when they sneak, after cosmetics.preview-seconds, when they quit or when the server stops, and puts them back exactly
 * where they were with their game mode. There is no repeating task: one delayed task ends each preview.
 */
public class PreviewService implements Listener {

    private record Session(Location origin, GameMode gameMode, List<Entity> entities, BukkitTask ending) {
    }

    private final CopperHeist plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PreviewService(CopperHeist plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isPreviewing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Starts previewing a shop keeper cosmetic; tells the player why when it can't. Always true once handled. */
    public boolean start(Player player, CosmeticDefinition cosmetic) {
        var messages = plugin.getMessageService();
        Location stage = plugin.getPreviewStage().get();
        if (stage == null) {
            player.sendMessage(messages.get(player, "cosmetics.preview-no-stage"));
            return true;
        }
        if (plugin.getGameManager().getGame(player) != null) {
            player.sendMessage(messages.get(player, "cosmetics.preview-hub-only"));
            return true;
        }
        if (isPreviewing(player)) return true;

        Location cameraSpot = plugin.getPreviewStage().camera(plugin.settings().getDouble("cosmetics.preview-distance", 3.5));
        NpcHandle handle = plugin.getShopKeepers().spawn(stage, Team.COPPER, cosmetic);
        if (handle == null) {
            player.sendMessage(messages.get(player, "cosmetics.menu.no-preview"));
            return true;
        }
        ArmorStand camera = cameraSpot.getWorld().spawn(cameraSpot, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
        });
        List<Entity> shown = new ArrayList<>(NpcSpawner.entities(handle));
        shown.add(camera);
        for (Entity entity : shown) {
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            player.showEntity(plugin, entity);
        }

        int seconds = Math.max(3, plugin.settings().getInt("cosmetics.preview-seconds", 12));
        Session session = new Session(player.getLocation(), player.getGameMode(), shown,
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> end(player), seconds * 20L));
        sessions.put(player.getUniqueId(), session);

        player.closeInventory();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(cameraSpot);
        // Spectating needs the player to have settled into spectator mode and to have been shown the camera first.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(player.getUniqueId()) == session) player.setSpectatorTarget(camera);
        }, 3L);
        Component name = Theme.mini().deserialize(plugin.getCosmetics().displayName(cosmetic));
        player.showTitle(Title.title(name, messages.get(player, "cosmetics.preview-sneak"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(seconds - 1), Duration.ofMillis(500))));
        return true;
    }

    /** Ends the player's preview (if any): removes the keeper and camera, restores their game mode and puts them back. */
    public void end(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.ending().cancel();
        player.resetTitle();
        player.setSpectatorTarget(null);
        for (Entity entity : session.entities()) entity.remove();
        player.setGameMode(session.gameMode());
        player.teleport(session.origin());
    }

    public void endAll() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) end(player);
        }
    }

    /** Sneaking is how a spectating player asks to stop spectating: that ends the preview instead. */
    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (!isPreviewing(player)) return;
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> end(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer());
    }
}
