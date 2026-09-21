package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.command.PartyCommands;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/** Everyone online who is not in a party, as player heads: click one to send them a party invite. Opened from the party menu. */
public class InvitePlayersMenu extends PagedMenu<Player> {

    public InvitePlayersMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "party.menu.invite-title"));
    }

    @Override
    protected boolean isValid() {
        return plugin.getParties().builtIn() != null;
    }

    @Override
    protected List<Player> items() {
        var service = plugin.getParties().builtIn();
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.equals(viewer) && service.partyOf(player.getUniqueId()).isEmpty())
                .map(player -> (Player) player)
                .toList();
    }

    @Override
    protected ItemStack icon(Player player) {
        ItemStack head = Gui.item(Material.PLAYER_HEAD, "<primary>" + player.getName(),
                List.of(plugin.getMessageService().rawFor(viewer, "party.menu.invite-click")));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    protected void onSelect(Player player, Click click) {
        if (PartyCommands.invite(plugin, plugin.getParties().builtIn(), viewer, player)) Gui.click(viewer);
        else Gui.deny(viewer);
        refresh();
    }
}
