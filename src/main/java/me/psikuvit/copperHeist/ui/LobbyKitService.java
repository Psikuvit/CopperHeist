package me.psikuvit.copperHeist.ui;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and hands out the items players see outside of a match: the hub's
 * join compass and guide book, and the "leave arena" item shown while
 * waiting in an arena for a match to start (doc §12).
 */
public class LobbyKitService {

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration guide;

    public LobbyKitService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "guide.yml");
        if (!file.exists()) plugin.saveResource("guide.yml", false);
        guide = YamlConfiguration.loadConfiguration(file);
    }

    public void giveHubKit(Player player) {
        player.getInventory().setItem(0, createJoinCompass());
        player.getInventory().setItem(1, createGuideBook());
    }

    public void giveLeaveItem(Player player) {
        player.getInventory().setItem(8, createLeaveItem());
    }

    public void sendArenaList(Player player) {
        boolean any = false;
        for (Arena arena : plugin.getArenaManager().all()) {
            if (!arena.isEnabled()) continue;
            any = true;

            Game game = plugin.getGameManager().peek(arena);
            String state = game != null ? game.getState().name() : "WAITING";
            int players = game != null ? game.totalPlayers() : 0;

            Component line = Component.text("[Join] ", NamedTextColor.GREEN)
                    .append(Component.text(arena.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" (" + state + ", " + players + " players)", NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.runCommand("/ch join " + arena.getName()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to join " + arena.getName(), NamedTextColor.GREEN)));
            player.sendMessage(line);
        }
        if (!any) player.sendMessage(Component.text("No arenas are open right now.", NamedTextColor.RED));
    }

    private ItemStack createJoinCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "join_compass");

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Join Arena", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to see open arenas", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuideBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        if (item.getItemMeta() instanceof BookMeta meta) {
            meta.title(miniMessage.deserialize(guide.getString("book-title", "Copper Heist Guide")));
            meta.author(miniMessage.deserialize(guide.getString("book-author", "Copper Heist")));
            List<Component> pages = new ArrayList<>();
            for (String page : guide.getStringList("pages")) {
                pages.add(miniMessage.deserialize(page));
            }
            meta.pages(pages);
            item.setItemMeta(meta);
        }
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "guide_book");
        return item;
    }

    private ItemStack createLeaveItem() {
        ItemStack item = new ItemStack(Material.RED_BED);
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "leave_arena");

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Leave Arena", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
