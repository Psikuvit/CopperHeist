package me.psikuvit.copperHeist.ui;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and hands out the items players see outside of a match: the hub's
 * join compass and guide book, and the "leave arena" item shown while
 * waiting in an arena for a match to start.
 */
public class LobbyKitService {

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = Theme.mini();
    private FileConfiguration guide;

    public LobbyKitService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        guide = ConfigFiles.load(plugin, "guide.yml");
    }

    public void giveHubKit(Player player) {
        player.getInventory().setItem(0, createJoinCompass(player));
        player.getInventory().setItem(1, createGuideBook(player));
        if (plugin.getCosmetics() != null && plugin.getCosmetics().enabled()) player.getInventory().setItem(2, createCosmeticsChest(player));
        boolean goals = plugin.getQuests().enabled() || plugin.getAchievements().enabled() || plugin.getDaily().enabled();
        if (goals) player.getInventory().setItem(3, createGoalsBook(player));
        player.getInventory().setItem(4, createProfileHead(player));
    }

    private ItemStack createGoalsBook(Player viewer) {
        var messages = plugin.getMessageService();
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "goals");

        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get(viewer, "lobby.goals-name").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(messages.get(viewer, "lobby.goals-lore").decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    /** The player's own head, skinned as them: opens the profile menu (level, stats, party, shortcuts into the other menus). */
    private ItemStack createProfileHead(Player viewer) {
        var messages = plugin.getMessageService();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "profile");

        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(viewer);
            meta.displayName(messages.get(viewer, "lobby.profile-name").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(messages.get(viewer, "lobby.profile-lore").decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCosmeticsChest(Player viewer) {
        var messages = plugin.getMessageService();
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "cosmetics");

        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get(viewer, "lobby.cosmetics-name").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(messages.get(viewer, "lobby.cosmetics-lore").decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    public void giveLeaveItem(Player player) {
        player.getInventory().setItem(8, createLeaveItem(player));
    }

    public void sendArenaList(Player player) {
        var messages = plugin.getMessageService();
        boolean any = false;
        for (Arena arena : plugin.getArenaManager().all()) {
            if (!arena.isEnabled()) continue;
            any = true;

            Game game = plugin.getGameManager().peek(arena);
            String state = game != null ? game.getState().name() : GameState.WAITING.name();
            int players = game != null ? game.totalPlayers() : 0;

            Component line = messages.get(player, "lobby.join-line", "arena", arena.getName(), "state", state, "players", players)
                    .clickEvent(ClickEvent.runCommand("/ch join " + arena.getName()))
                    .hoverEvent(HoverEvent.showText(messages.get(player, "lobby.join-hover", "arena", arena.getName())));
            player.sendMessage(line);
        }
        if (!any) player.sendMessage(messages.get(player, "lobby.no-arenas"));
    }

    private ItemStack createJoinCompass(Player viewer) {
        var messages = plugin.getMessageService();
        ItemStack item = new ItemStack(Material.COMPASS);
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "join_compass");

        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get(viewer, "lobby.compass-name").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(messages.get(viewer, "lobby.compass-lore").decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    /** The guide book: a translation's guide.pages (in lang/) wins over the single-language guide.yml. */
    private ItemStack createGuideBook(Player viewer) {
        var messages = plugin.getMessageService();
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        if (item.getItemMeta() instanceof BookMeta meta) {
            String title = messages.rawOrNull(viewer, "guide.title");
            String author = messages.rawOrNull(viewer, "guide.author");
            List<String> pageTexts = messages.listOrNull(viewer, "guide.pages");
            meta.title(miniMessage.deserialize(title != null ? title : guide.getString("book-title", "Copper Heist Guide")));
            meta.author(miniMessage.deserialize(author != null ? author : guide.getString("book-author", "Copper Heist")));
            List<Component> pages = new ArrayList<>();
            for (String page : pageTexts != null ? pageTexts : guide.getStringList("pages")) {
                pages.add(miniMessage.deserialize(page));
            }
            meta.pages(pages);
            item.setItemMeta(meta);
        }
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "guide_book");
        return item;
    }

    private ItemStack createLeaveItem(Player viewer) {
        ItemStack item = new ItemStack(Material.RED_BED);
        Pdc.set(item, PdcKeys.LOBBY_ITEM, "leave_arena");

        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getMessageService().get(viewer, "lobby.leave-name").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
