package me.psikuvit.copperHeist.menu.party;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.command.Msg;
import me.psikuvit.copperHeist.command.PartyCommands;
import me.psikuvit.copperHeist.menu.Click;
import me.psikuvit.copperHeist.menu.Gui;
import me.psikuvit.copperHeist.menu.PagedMenu;
import me.psikuvit.copperHeist.menu.arena.ArenaMenu;
import me.psikuvit.copperHeist.party.PartyInfo;
import me.psikuvit.copperHeist.party.PartyService;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Your built-in party: its members as player heads (the leader can kick with right-click or promote with shift-click), a button to invite
 * people, one to leave or disband, and one that opens the arena picker so the leader can queue everyone together. Opened by /party.
 */
public class PartyMenu extends PagedMenu<UUID> {

    public PartyMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "party.menu.title"));
    }

    @Override
    protected boolean isValid() {
        return plugin.getParties().builtIn() != null;
    }

    private Optional<PartyInfo> party() {
        return plugin.getParties().builtIn().partyOf(viewer.getUniqueId());
    }

    @Override
    protected List<UUID> items() {
        return party().map(PartyInfo::members).orElse(List.of());
    }

    @Override
    protected ItemStack icon(UUID member) {
        var messages = plugin.getMessageService();
        Optional<PartyInfo> party = party();
        OfflinePlayer who = Bukkit.getOfflinePlayer(member);
        boolean leader = party.isPresent() && party.get().isLeader(member);
        boolean online = who.isOnline();

        List<String> lore = new ArrayList<>();
        lore.add(messages.rawFor(viewer, online ? "party.online" : "party.offline"));
        if (party.isPresent() && party.get().isLeader(viewer.getUniqueId()) && !member.equals(viewer.getUniqueId())) {
            lore.add("");
            lore.add(messages.rawFor(viewer, "party.menu.kick-hint"));
            lore.add(messages.rawFor(viewer, "party.menu.promote-hint"));
        }
        String name = (leader ? "<accent>" + messages.rawFor(viewer, "party.menu.leader-mark") + " " : "<primary>") + who.getName();
        ItemStack head = Gui.item(Material.PLAYER_HEAD, name, lore);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(who);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    protected void onSelect(UUID member, Click click) {
        PartyService service = plugin.getParties().builtIn();
        Optional<PartyInfo> party = party();
        Player target = Bukkit.getPlayer(member);
        if (party.isEmpty() || !party.get().isLeader(viewer.getUniqueId()) || member.equals(viewer.getUniqueId()) || target == null) {
            Gui.deny(viewer);
            return;
        }
        boolean done = click.isRight() ? PartyCommands.kick(plugin, service, viewer, target)
                : click.isShift() && PartyCommands.promote(plugin, service, viewer, target);
        if (done) Gui.click(viewer);
        else Gui.deny(viewer);
        refresh();
    }

    @Override
    protected void drawFooter(int bottom) {
        var messages = plugin.getMessageService();
        PartyService service = plugin.getParties().builtIn();
        Optional<PartyInfo> party = party();
        boolean leader = party.isEmpty() || party.get().isLeader(viewer.getUniqueId());

        if (leader) {
            set(bottom + 1, Gui.item(Material.WRITABLE_BOOK, messages.rawFor(viewer, "party.menu.invite"),
                    List.of(messages.rawFor(viewer, "party.menu.invite-lore"))), click -> {
                Gui.click(click.player());
                openChild(new InvitePlayersMenu(plugin, viewer));
            });
        }
        if (party.isPresent()) {
            set(bottom + 2, Gui.item(Material.COMPASS, messages.rawFor(viewer, "party.menu.queue"),
                    List.of(messages.rawFor(viewer, leader ? "party.menu.queue-lore" : "party.menu.queue-member"))), click -> {
                Gui.click(click.player());
                if (leader) openChild(new ArenaMenu(plugin, viewer));
            });
            boolean disband = party.get().isLeader(viewer.getUniqueId());
            set(bottom + 7, Gui.item(Material.RED_BED, messages.rawFor(viewer, disband ? "party.menu.disband" : "party.menu.leave"),
                    List.of()), click -> {
                Gui.click(click.player());
                if (disband) {
                    PartyCommands.disband(plugin, service, viewer);
                } else {
                    PartyService.Departure departure = service.leave(viewer.getUniqueId());
                    Msg.ok(viewer, "party.left");
                    PartyCommands.announceDeparture(plugin, viewer.getUniqueId(), viewer.getName(), departure, "party.member-left");
                }
                refresh();
            });
        }
    }
}
