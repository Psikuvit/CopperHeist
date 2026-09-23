package me.psikuvit.copperHeist.menu.arena;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.menu.Gui;
import me.psikuvit.copperHeist.menu.Menu;
import me.psikuvit.copperHeist.network.RemoteArena;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The arena picker the join compass opens: one framed tile per arena (and per arena on other servers of the network), coloured
 * by whether you can join it right now, with its state, player count and preset. It refreshes every second so the states stay
 * live; clicking a joinable tile runs /ch join.
 */
public class ArenaMenu extends Menu {

    private record Tile(Material material, String name, List<String> lore, String joinId) {
    }

    public ArenaMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "gui.arena.title"));
    }

    @Override
    protected int rows() {
        return Gui.rowsFor(Math.max(1, tiles().size()));
    }

    @Override
    protected int refreshTicks() {
        return 20;
    }

    @Override
    protected void draw() {
        border(Material.GRAY_STAINED_GLASS_PANE);
        List<Tile> tiles = tiles();
        if (tiles.isEmpty()) {
            var messages = plugin.getMessageService();
            set(13, Gui.item(Material.BARRIER, messages.rawFor(viewer, "gui.arena.none-name"),
                    List.of(messages.rawFor(viewer, "gui.arena.none-lore"))));
        }
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            ItemStack item = Gui.item(tile.material(), tile.name(), tile.lore());
            if (tile.joinId() == null) {
                set(Gui.slotFor(i), item, click -> Gui.deny(click.player()));
                continue;
            }
            Gui.glow(item);
            set(Gui.slotFor(i), item, click -> {
                Gui.click(click.player());
                close();
                click.player().performCommand("ch join " + tile.joinId());
            });
        }
        closeButton();
    }

    private List<Tile> tiles() {
        var messages = plugin.getMessageService();
        List<Tile> tiles = new ArrayList<>();
        int max = plugin.settings().getInt("match.max-players", 16);

        for (Arena arena : plugin.getArenaManager().all()) {
            if (!arena.isEnabled()) continue;
            Game game = plugin.getGameManager().peek(arena);
            GameState state = game == null ? GameState.WAITING : game.getState();
            int players = game == null ? 0 : game.totalPlayers();
            int arenaMax = game == null ? max : game.settings().getInt("match.max-players", max);
            List<String> lore = new ArrayList<>();
            lore.add(messages.rawFor(viewer, "gui.arena.state." + state.name().toLowerCase(Locale.ROOT)));
            lore.add(messages.rawFor(viewer, "gui.arena.players", "players", players, "max", arenaMax));
            if (arena.getPreset() != null) lore.add(messages.rawFor(viewer, "gui.arena.preset", "preset", arena.getPreset()));
            boolean joinable = joinable(state, players, arenaMax);
            lore.add("");
            lore.add(messages.rawFor(viewer, joinable ? "gui.arena.click" : "gui.arena.unavailable"));
            tiles.add(new Tile(materialFor(state, joinable), "<primary>" + arena.getName(), lore, joinable ? arena.getName() : null));
        }

        for (RemoteArena remote : plugin.getNetwork().remoteArenas()) {
            if (!remote.enabled()) continue;
            GameState state = stateOf(remote.state());
            boolean joinable = remote.joinable();
            List<String> lore = new ArrayList<>();
            lore.add(messages.rawFor(viewer, "gui.arena.state." + state.name().toLowerCase(Locale.ROOT)));
            lore.add(messages.rawFor(viewer, "gui.arena.players", "players", remote.players(), "max", remote.maxPlayers()));
            lore.add(messages.rawFor(viewer, "gui.arena.server", "server", remote.serverId()));
            lore.add("");
            lore.add(messages.rawFor(viewer, joinable ? "gui.arena.click" : "gui.arena.unavailable"));
            tiles.add(new Tile(materialFor(state, joinable), "<primary>" + remote.arena() + " <dim>@ " + remote.serverId(), lore,
                    joinable ? remote.fullName() : null));
        }
        return tiles;
    }

    private static boolean joinable(GameState state, int players, int max) {
        return (state == GameState.WAITING || state == GameState.STARTING) && players < max;
    }

    private static Material materialFor(GameState state, boolean joinable) {
        if (joinable) return state == GameState.STARTING ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE;
        return switch (state) {
            case ENDING, RESETTING -> Material.ORANGE_CONCRETE;
            case WAITING, STARTING -> Material.GRAY_CONCRETE; // full
            default -> Material.RED_CONCRETE;
        };
    }

    private static GameState stateOf(String name) {
        try {
            return GameState.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return GameState.WAITING;
        }
    }
}
