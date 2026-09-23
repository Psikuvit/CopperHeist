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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Builds and hands out the items players see outside of a match: the hub's
 * join compass and guide book, and the "leave arena" item shown while
 * waiting in an arena for a match to start. Each item's hotbar slot is configurable
 * (lobby.slots.*); -1 hides that item even if its feature is otherwise on.
 */
public class LobbyKitService {

    /** A hub kit item: its config key, default slot and how to build it. */
    private enum Kit {
        JOIN_COMPASS("join-compass", 0, LobbyKitService::createJoinCompass),
        GUIDE_BOOK("guide-book", 1, LobbyKitService::createGuideBook),
        COSMETICS("cosmetics", 2, LobbyKitService::createCosmeticsChest),
        GOALS("goals", 3, LobbyKitService::createGoalsBook),
        PROFILE("profile", 4, LobbyKitService::createProfileHead),
        LEAVE_ARENA("leave-arena", 8, LobbyKitService::createLeaveItem);

        final String key;
        final int defaultSlot;
        final BiFunction<LobbyKitService, Player, ItemStack> factory;

        Kit(String key, int defaultSlot, BiFunction<LobbyKitService, Player, ItemStack> factory) {
            this.key = key;
            this.defaultSlot = defaultSlot;
            this.factory = factory;
        }
    }

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = Theme.mini();
    private final Set<String> warnedSlots = new HashSet<>();
    private FileConfiguration guide;

    public LobbyKitService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        guide = ConfigFiles.load(plugin, "guide.yml");
        warnedSlots.clear();
        checkForClashingSlots();
    }

    public void giveHubKit(Player player) {
        give(player, Kit.JOIN_COMPASS);
        give(player, Kit.GUIDE_BOOK);
        if (plugin.getCosmetics() != null && plugin.getCosmetics().enabled()) give(player, Kit.COSMETICS);
        boolean goals = plugin.getQuests().enabled() || plugin.getAchievements().enabled() || plugin.getDaily().enabled();
        if (goals) give(player, Kit.GOALS);
        give(player, Kit.PROFILE);
    }

    private void give(Player player, Kit kit) {
        int slot = slotFor(kit);
        if (slot < 0) return; // lobby.slots.<key>: -1 hides this item
        player.getInventory().setItem(slot, kit.factory.apply(this, player));
    }

    /** lobby.slots.<key>: 0-8 (a hotbar slot) or -1 to hide that item; anything else falls back to the default slot. */
    private int slotFor(Kit kit) {
        int value = plugin.settings().getInt("lobby.slots." + kit.key, kit.defaultSlot);
        if (value == -1) return -1;
        if (value < 0 || value > 8) {
            if (warnedSlots.add(kit.key)) {
                plugin.getLogger().warning("lobby.slots." + kit.key + " (" + value + ") must be 0-8 or -1 - using " + kit.defaultSlot + ".");
            }
            return kit.defaultSlot;
        }
        return value;
    }

    /**
     * Warns once per reload if two hub kit items would land on the same slot (they are all given together on join; the leave item is
     * given later, in an empty inventory, so it is never part of this check).
     */
    private void checkForClashingSlots() {
        Map<Integer, Kit> bySlot = new HashMap<>();
        for (Kit kit : new Kit[]{Kit.JOIN_COMPASS, Kit.GUIDE_BOOK, Kit.COSMETICS, Kit.GOALS, Kit.PROFILE}) {
            int slot = slotFor(kit);
            if (slot < 0) continue;
            Kit clash = bySlot.putIfAbsent(slot, kit);
            if (clash != null) {
                plugin.getLogger().warning("lobby.slots." + kit.key + " and lobby.slots." + clash.key
                        + " are both slot " + slot + " - " + kit.key + " will replace " + clash.key + " for players who get both.");
            }
        }
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
        give(player, Kit.LEAVE_ARENA);
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
