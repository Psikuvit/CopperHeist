package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Handles the hub's join compass and the arena waiting room's leave item. */
public class LobbyListener implements Listener {

    private final CopperHeist plugin;

    public LobbyListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        String action = Pdc.get(item, PdcKeys.LOBBY_ITEM);
        if (action == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        switch (action) {
            case "join_compass" -> plugin.getLobbyKitService().sendArenaList(player);
            case "leave_arena" -> {
                if (plugin.getGameManager().getGame(player) != null) {
                    plugin.getGameManager().leave(player);
                    player.sendMessage(Component.text("You left the arena.", NamedTextColor.GREEN));
                }
            }
            default -> {
            }
        }
    }
}
