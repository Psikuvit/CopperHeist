package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.network.RemoteArena;
import me.psikuvit.copperHeist.ui.Theme;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The arena picker the join compass opens: one framed tile per arena (and per arena on other servers of the network), coloured
 * by whether you can join it right now, with its state, player count and preset. Clicking a joinable tile runs /ch join.
 */
public class ArenaMenu {

    /** Marks inventories as this menu so the click handler can recognise them. */
    public static final class Holder implements InventoryHolder {
        @Override
        public @NonNull Inventory getInventory() {
            throw new UnsupportedOperationException("the menu is the inventory");
        }
    }

    private record Tile(Material material, String name, List<String> lore, String joinId) {
    }

    private final CopperHeist plugin;

    public ArenaMenu(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
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
            lore.add(messages.rawFor(player, "gui.arena.state." + state.name().toLowerCase(Locale.ROOT)));
            lore.add(messages.rawFor(player, "gui.arena.players", "players", players, "max", arenaMax));
            if (arena.getPreset() != null) lore.add(messages.rawFor(player, "gui.arena.preset", "preset", arena.getPreset()));
            boolean joinable = joinable(state, players, arenaMax);
            lore.add("");
            lore.add(messages.rawFor(player, joinable ? "gui.arena.click" : "gui.arena.unavailable"));
            tiles.add(new Tile(materialFor(state, joinable), "<primary>" + arena.getName(), lore, joinable ? arena.getName() : null));
        }

        for (RemoteArena remote : plugin.getNetwork().remoteArenas()) {
            if (!remote.enabled()) continue;
            GameState state = stateOf(remote.state());
            boolean joinable = remote.joinable();
            List<String> lore = new ArrayList<>();
            lore.add(messages.rawFor(player, "gui.arena.state." + state.name().toLowerCase(Locale.ROOT)));
            lore.add(messages.rawFor(player, "gui.arena.players", "players", remote.players(), "max", remote.maxPlayers()));
            lore.add(messages.rawFor(player, "gui.arena.server", "server", remote.serverId()));
            lore.add("");
            lore.add(messages.rawFor(player, joinable ? "gui.arena.click" : "gui.arena.unavailable"));
            tiles.add(new Tile(materialFor(state, joinable), "<primary>" + remote.arena() + " <dim>@ " + remote.serverId(), lore,
                    joinable ? remote.fullName() : null));
        }

        int rows = Gui.rowsFor(Math.max(1, tiles.size()));
        Inventory inventory = Bukkit.createInventory(new Holder(), rows * 9, Theme.mini().deserialize(messages.rawFor(player, "gui.arena.title")));
        Gui.border(inventory, Material.GRAY_STAINED_GLASS_PANE);

        if (tiles.isEmpty()) {
            inventory.setItem(13, Gui.item(Material.BARRIER, messages.rawFor(player, "gui.arena.none-name"),
                    List.of(messages.rawFor(player, "gui.arena.none-lore")), null));
        }
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            ItemStack item = Gui.item(tile.material(), tile.name(), tile.lore(), tile.joinId() == null ? "unavailable" : tile.joinId());
            if (tile.joinId() != null) Gui.glow(item);
            inventory.setItem(Gui.slotFor(i), item);
        }
        player.openInventory(inventory);
        Gui.open(player);
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
