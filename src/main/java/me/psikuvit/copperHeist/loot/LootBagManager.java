package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.LootStolenEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.task.LootBagTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Death/disconnect drops for one {@link Game}. A bag is an ItemDisplay with an
 * Interaction hitbox and a TextDisplay value label; anyone can right-click it
 * to take what fits under their carry limit. Unclaimed bags scatter their
 * loot back onto the arena's loot points when they expire.
 */
public class LootBagManager {

    private final CopperHeist plugin;
    private final Game game;
    private final List<LootBag> bags = new ArrayList<>();
    private BukkitTask task;

    public LootBagManager(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start() {
        task = new LootBagTask(this).runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (LootBag bag : new ArrayList<>(bags)) remove(bag);
    }

    public LootBag create(Location location, List<ItemStack> items) {
        if (items.isEmpty() || location.getWorld() == null) return null;
        Location base = location.clone().add(0, 0.5, 0);
        long lifetime = plugin.getConfig().getLong("loot.bag-despawn-seconds", 45) * 1000L;

        ItemDisplay display = base.getWorld().spawn(base, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(Material.BUNDLE));
            entity.setPersistent(true);
        });
        Interaction hitbox = base.getWorld().spawn(base, Interaction.class, entity -> {
            entity.setInteractionWidth(0.9f);
            entity.setInteractionHeight(0.9f);
            entity.setPersistent(true);
        });
        TextDisplay label = base.getWorld().spawn(base.clone().add(0, 0.8, 0), TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
        });

        LootBag bag = new LootBag(new ArrayList<>(items), display, hitbox, label, System.currentTimeMillis() + lifetime);
        bags.add(bag);
        plugin.getGameManager().registerHeistEntity(game, hitbox.getUniqueId());
        updateLabel(bag);
        return bag;
    }

    public LootBag findByEntity(UUID entityId) {
        for (LootBag bag : bags) {
            if (bag.getHitbox().getUniqueId().equals(entityId)) return bag;
        }
        return null;
    }

    public void pickup(Player player, LootBag bag) {
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null || !game.isActive()) return;

        boolean tookAny = false;
        Iterator<ItemStack> it = bag.getItems().iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (!plugin.getLootWeightService().canCarry(player, item)) continue;

            int value = LootItem.getValue(item) * item.getAmount();
            Team lastTeam = LootItem.getLastTeam(item);
            if (lastTeam != null && lastTeam != gp.getTeam()) {
                game.getTeam(gp.getTeam()).addSteal();
                Bukkit.getPluginManager().callEvent(new LootStolenEvent(game, player, lastTeam, value));
            }
            LootItem.setLastTeam(item, gp.getTeam());
            LootItem.setLastCarrier(item, player.getUniqueId());

            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            it.remove();
            tookAny = true;
        }

        if (!tookAny) {
            player.sendActionBar(plugin.getMessageService().get("actionbar.carry-limit",
                    "limit", plugin.getConfig().getInt("loot.carry-limit", 80)));
            return;
        }
        game.getRoleService().breakInvisibility(player);
        if (bag.getItems().isEmpty()) remove(bag);
        else updateLabel(bag);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (LootBag bag : new ArrayList<>(bags)) {
            if (now >= bag.getExpiresAtMillis()) {
                scatter(bag);
                remove(bag);
            } else {
                updateLabel(bag);
            }
        }
    }

    /** Expired loot goes back to a random loot point rather than vanishing. */
    private void scatter(LootBag bag) {
        List<Location> points = game.getArena().getLootPoints();
        for (ItemStack item : bag.getItems()) {
            Location where = points.isEmpty() ? bag.getHitbox().getLocation()
                    : points.get(ThreadLocalRandom.current().nextInt(points.size())).clone().add(0.5, 0.5, 0.5);
            if (where.getWorld() == null) continue;
            Item dropped = where.getWorld().dropItem(where, item);
            dropped.setUnlimitedLifetime(true);
        }
    }

    private void remove(LootBag bag) {
        bags.remove(bag);
        plugin.getGameManager().unregisterHeistEntity(bag.getHitbox().getUniqueId());
        bag.getDisplay().remove();
        bag.getHitbox().remove();
        bag.getLabel().remove();
    }

    private void updateLabel(LootBag bag) {
        long seconds = Math.max(0, (bag.getExpiresAtMillis() - System.currentTimeMillis()) / 1000);
        bag.getLabel().text(Component.text("Loot Bag [" + bag.totalValue() + "] " + seconds + "s", NamedTextColor.YELLOW));
    }
}
