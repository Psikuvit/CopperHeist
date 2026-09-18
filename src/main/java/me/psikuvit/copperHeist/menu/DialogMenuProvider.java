package me.psikuvit.copperHeist.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.role.RoleDefinition;
import me.psikuvit.copperHeist.shop.ShopEntry;
import me.psikuvit.copperHeist.ui.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The shop and role picker as native Paper dialogs (the modern pop-up screen) instead of a chest GUI.
 * Buttons run on the main thread; the shop dialog stays open so several items can be bought in a row.
 */
public class DialogMenuProvider implements MenuProvider {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final CopperHeist plugin;

    public DialogMenuProvider(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openShop(Player player, Game game, GamePlayer gamePlayer) {
        List<ActionButton> buttons = new ArrayList<>();
        for (ShopEntry entry : plugin.getShopService().entries()) {
            Component label = MINI.deserialize(plugin.getShopService().entryName(entry, player)).append(Component.text(" - " + entry.cost()));
            List<Component> lore = new ArrayList<>();
            for (String line : plugin.getShopService().entryLore(entry, player)) lore.add(MINI.deserialize(line));
            buttons.add(ActionButton.builder(label)
                    .tooltip(Component.join(JoinConfiguration.newlines(), lore))
                    .width(200)
                    .action(callback(player.getUniqueId(), () -> {
                        Game current = plugin.getGameManager().getGame(player);
                        GamePlayer gp = current == null ? null : current.getGamePlayer(player.getUniqueId());
                        if (gp != null) plugin.getShopService().purchase(player, entry, current, gp);
                    }))
                    .build());
        }
        show(player, MINI.deserialize(plugin.getShopService().menuTitle(player)),
                plugin.getMessageService().get(player, "menu.shop-intro"), buttons);
    }

    @Override
    public void openRoles(Player player, Game game, GamePlayer gamePlayer) {
        List<ActionButton> buttons = new ArrayList<>();
        for (RoleDefinition role : plugin.getRoleRegistry().all()) {
            String description = game.getRoleService().description(role, player);
            buttons.add(ActionButton.builder(Component.text(game.getRoleService().displayName(role, player), role.color()))
                    .tooltip(description.isBlank() ? null : MINI.deserialize(description))
                    .width(200)
                    .action(callback(player.getUniqueId(), () -> {
                        Game current = plugin.getGameManager().getGame(player);
                        GamePlayer gp = current == null ? null : current.getGamePlayer(player.getUniqueId());
                        if (gp == null) return;
                        Text error = current.getRoleService().trySetRole(gp, role);
                        var messages = plugin.getMessageService();
                        player.sendMessage(error != null ? messages.err(player, error)
                                : messages.ok(player, current.isActive() ? "command.role-set-next" : "command.role-set", "role", role.displayName()));
                    }))
                    .build());
        }
        var messages = plugin.getMessageService();
        show(player, messages.get(player, "menu.role-title"), messages.get(player, "menu.role-intro"), buttons);
    }

    private DialogAction callback(UUID viewer, Runnable task) {
        return DialogAction.customClick((view, audience) -> Bukkit.getScheduler().runTask(plugin, task),
                ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
    }

    private void show(Player player, Component title, Component intro, List<ActionButton> buttons) {
        ActionButton close = ActionButton.builder(plugin.getMessageService().get(player, "menu.close")).width(120).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(intro)))
                        .build())
                .type(DialogType.multiAction(buttons, close, 2)));
        player.showDialog(dialog);
    }
}
