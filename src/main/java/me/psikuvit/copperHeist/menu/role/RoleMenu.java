package me.psikuvit.copperHeist.menu.role;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.menu.Gui;
import me.psikuvit.copperHeist.menu.Menu;
import me.psikuvit.copperHeist.role.RoleDefinition;
import me.psikuvit.copperHeist.role.RoleService;
import me.psikuvit.copperHeist.ui.Text;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The role picker, one icon per role. The role the viewer has now glows and says so; every other icon shows its ability, team
 * limit and how to choose it. A short list is centred in the middle row instead of hugging the left edge.
 */
public class RoleMenu extends Menu {

    public RoleMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "gui.roles.title"));
    }

    @Override
    protected int rows() {
        int count = plugin.getRoleRegistry().all().size();
        return count <= Gui.ITEMS_PER_ROW ? 3 : Gui.rowsFor(count);
    }

    @Override
    protected boolean isValid() {
        return plugin.getGameManager().getGame(viewer) != null;
    }

    @Override
    protected void draw() {
        border(Material.GRAY_STAINED_GLASS_PANE);
        List<RoleDefinition> all = plugin.getRoleRegistry().all();
        Game game = plugin.getGameManager().getGame(viewer);
        GamePlayer gp = game == null ? null : game.getGamePlayer(viewer.getUniqueId());
        String selected = gp == null || gp.getRole() == null ? null : gp.getRole().id();

        boolean centred = all.size() <= Gui.ITEMS_PER_ROW;
        int offset = all.size() < Gui.ITEMS_PER_ROW ? (Gui.ITEMS_PER_ROW - all.size()) / 2 : 0;
        for (int i = 0; i < all.size(); i++) {
            RoleDefinition role = all.get(i);
            int slot = centred ? 9 + 1 + offset + i : Gui.slotFor(i);
            set(slot, icon(role, game, role.id().equals(selected)), click -> choose(role));
        }
        closeButton();
    }

    private void choose(RoleDefinition role) {
        Game game = plugin.getGameManager().getGame(viewer);
        GamePlayer gp = game == null ? null : game.getGamePlayer(viewer.getUniqueId());
        if (gp == null) return;

        Text error = game.getRoleService().trySetRole(gp, role);
        var messages = plugin.getMessageService();
        if (error != null) {
            Gui.deny(viewer);
            close();
            viewer.sendMessage(messages.err(viewer, error));
            return;
        }
        Gui.success(viewer);
        close();
        viewer.sendMessage(messages.ok(viewer, game.isActive() ? "command.role-set-next" : "command.role-set", "role", role.displayName()));
    }

    private ItemStack icon(RoleDefinition role, Game game, boolean selected) {
        var messages = plugin.getMessageService();
        RoleService roles = game == null ? null : game.getRoleService();
        ItemStack icon = new ItemStack(role.icon());
        ItemMeta meta = icon.getItemMeta();
        Component name = Component.text(roles == null ? role.displayName() : roles.displayName(role, viewer), role.color());
        meta.displayName(Gui.plain(selected ? name.decorate(TextDecoration.BOLD) : name));

        List<Component> lore = new ArrayList<>();
        String description = roles == null ? role.description() : roles.description(role, viewer);
        if (!description.isBlank()) lore.add(Gui.text(description));
        lore.add(Component.empty());
        if (role.hasAbility()) {
            lore.add(Gui.text(messages.rawFor(viewer, "gui.roles.ability", "ability", role.ability().id().replace('-', ' '),
                    "cooldown", role.ability().cooldownSeconds())));
        }
        lore.add(Gui.text(messages.rawFor(viewer, "gui.roles.limit", "limit", plugin.getRoleRegistry().maxPerTeam(role))));
        lore.add(Component.empty());
        lore.add(Gui.text(messages.rawFor(viewer, selected ? "gui.roles.selected" : "gui.roles.click")));
        meta.lore(lore);
        icon.setItemMeta(meta);
        if (selected) Gui.glow(icon);
        return icon;
    }
}
