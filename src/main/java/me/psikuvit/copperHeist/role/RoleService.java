package me.psikuvit.copperHeist.role;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.role.ability.AbilityContext;
import me.psikuvit.copperHeist.role.ability.RoleAbility;
import me.psikuvit.copperHeist.ui.Text;
import me.psikuvit.copperHeist.util.Cooldowns;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies {@link RoleDefinition}s during a match: starting kits, permanent
 * effects, numeric passives and the F-key ability. Everything role-specific
 * comes from roles.yml - this class has no knowledge of any particular role.
 */
public class RoleService {

    public static final String PASSIVE_LOOT_WEIGHT = "loot-weight-multiplier";
    public static final String PASSIVE_SCRAPE_COOLDOWN = "scrape-cooldown-multiplier";
    public static final String PASSIVE_LOCKPICK = "lockpick-multiplier";
    public static final String PASSIVE_DAMAGE_BONUS = "damage-bonus";
    public static final String PASSIVE_DAMAGE_RADIUS = "damage-bonus-radius";
    public static final String PASSIVE_EXTRA_ALARMS = "extra-alarms";
    public static final String PASSIVE_CLEARS_STUN = "clears-stun";

    private final CopperHeist plugin;
    private final Game game;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Cooldowns abilityCooldowns = new Cooldowns();

    public RoleService(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    private RoleRegistry roles() {
        return plugin.getRoleRegistry();
    }

    /** Returns why the role couldn't be set, or null if it was (it applies on the next respawn during a match). */
    public Text trySetRole(GamePlayer gp, RoleDefinition role) {
        int max = roles().maxPerTeam(role);
        if (!role.id().equals(gp.getRole().id()) && countOnTeam(gp.getTeam(), role) >= max) {
            return Text.of("role.team-full", "count", max, "role", role.displayName());
        }
        gp.setRole(role);
        return null;
    }

    /** One item per role, in registry order - the click listener maps slot index straight back to the role. */
    public Inventory buildRoleMenu(Player viewer) {
        List<RoleDefinition> all = roles().all();
        int size = Math.max(9, (all.size() + 8) / 9 * 9);
        Inventory inventory = Bukkit.createInventory(new RoleHolder(), size,
                plugin.getMessageService().get(viewer, "menu.role-title").colorIfAbsent(NamedTextColor.GOLD));
        for (RoleDefinition role : all) {
            ItemStack icon = new ItemStack(role.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(displayName(role, viewer), role.color()).decoration(TextDecoration.ITALIC, false));
            String description = description(role, viewer);
            if (!description.isBlank()) {
                meta.lore(List.of(miniMessage.deserialize(description).decoration(TextDecoration.ITALIC, false)));
            }
            icon.setItemMeta(meta);
            inventory.addItem(icon);
        }
        return inventory;
    }

    /** A role's name in the viewer's language: lang key roles.&lt;id&gt;.name if a translation defines it, else roles.yml. */
    public String displayName(RoleDefinition role, Player viewer) {
        String translated = plugin.getMessageService().rawOrNull(viewer, "roles." + role.id() + ".name");
        return translated != null ? translated : role.displayName();
    }

    public String description(RoleDefinition role, Player viewer) {
        String translated = plugin.getMessageService().rawOrNull(viewer, "roles." + role.id() + ".description");
        return translated != null ? translated : role.description();
    }

    public int countOnTeam(Team team, RoleDefinition role) {
        int count = 0;
        for (UUID uuid : game.getTeam(team).getMembers()) {
            GamePlayer gp = game.getGamePlayer(uuid);
            if (gp != null && gp.getRole() != null && gp.getRole().id().equals(role.id())) count++;
        }
        return count;
    }

    /** Picks the first role (in file order) that isn't already full for this team - used as the join default. */
    public RoleDefinition defaultRole(Team team) {
        RoleDefinition preferred = roles().defaultRole();
        if (preferred != null && countOnTeam(team, preferred) < roles().maxPerTeam(preferred)) return preferred;
        for (RoleDefinition role : roles().all()) {
            if (countOnTeam(team, role) < roles().maxPerTeam(role)) return role;
        }
        return preferred;
    }

    // ---- loadouts ----

    public void giveLoadout(Player player, RoleDefinition role, Team team) {
        player.getInventory().clear();
        for (PotionEffect active : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(active.getType());

        for (Map.Entry<String, ArmorPiece> entry : role.armor().entrySet()) {
            ItemStack piece = new ItemStack(entry.getValue().material());
            if (entry.getValue().dyeTeam() && piece.getItemMeta() instanceof LeatherArmorMeta meta) {
                meta.setColor(team.armorColor());
                piece.setItemMeta(meta);
            }
            switch (entry.getKey()) {
                case "helmet" -> player.getInventory().setHelmet(piece);
                case "chestplate" -> player.getInventory().setChestplate(piece);
                case "leggings" -> player.getInventory().setLeggings(piece);
                case "boots" -> player.getInventory().setBoots(piece);
                default -> plugin.getLogger().warning("Role " + role.id() + " has unknown armor slot '" + entry.getKey() + "'");
            }
        }

        for (LoadoutItem item : role.items()) {
            ItemStack stack = buildItem(item);
            if (stack == null) continue;
            if (item.offhand()) player.getInventory().setItemInOffHand(stack);
            else player.getInventory().addItem(stack);
        }

        for (PotionEffect effect : role.effects()) player.addPotionEffect(effect);

        player.setGameMode(GameMode.SURVIVAL);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) player.setHealth(maxHealth.getValue());
        player.setFoodLevel(20);
        player.setFireTicks(0);
    }

    private ItemStack buildItem(LoadoutItem item) {
        ItemStack stack;
        if (item.shopItem() != null) {
            stack = createShopItem(item.shopItem());
            if (stack == null) {
                plugin.getLogger().warning("Role loadout references unknown shop item '" + item.shopItem() + "'");
                return null;
            }
            stack.setAmount(item.amount());
            return stack;
        }
        stack = new ItemStack(item.material(), item.amount());
        ItemMeta meta = stack.getItemMeta();
        if (item.name() != null) meta.displayName(miniMessage.deserialize(item.name()).decoration(TextDecoration.ITALIC, false));
        if (!item.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : item.lore()) lore.add(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        for (Map.Entry<String, Integer> enchant : item.enchants().entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(enchant.getKey().toLowerCase());
            Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
            if (enchantment != null) meta.addEnchant(enchantment, enchant.getValue(), true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createShopItem(String id) {
        return plugin.getShopService().createItem(id);
    }

    // ---- passives ----

    public double speedPenaltyMultiplier(RoleDefinition role) {
        return role.passive(PASSIVE_LOOT_WEIGHT, 1.0);
    }

    public double scrapeCooldownMultiplier(RoleDefinition role) {
        return role.passive(PASSIVE_SCRAPE_COOLDOWN, 1.0);
    }

    public double lockpickMultiplier(RoleDefinition role) {
        return role.passive(PASSIVE_LOCKPICK, 1.0);
    }

    /** Extra alarm slots granted by the roles currently on a team (Guards, by default). */
    public int extraAlarms(Team team) {
        int total = 0;
        for (UUID uuid : game.getTeam(team).getMembers()) {
            GamePlayer gp = game.getGamePlayer(uuid);
            if (gp != null && gp.getRole() != null) total += (int) gp.getRole().passive(PASSIVE_EXTRA_ALARMS, 0);
        }
        return total;
    }

    /** Bonus damage inside your own base - the arena's base region, or a radius around your spawn if none is set. */
    public double damageMultiplier(Player attacker, GamePlayer gp) {
        double bonus = gp.getRole().passive(PASSIVE_DAMAGE_BONUS, 0);
        if (bonus <= 0) return 1.0;
        double radius = gp.getRole().passive(PASSIVE_DAMAGE_RADIUS, 15.0);
        return game.isInBase(gp.getTeam(), attacker.getLocation(), radius) ? 1.0 + bonus : 1.0;
    }

    public boolean canClearStun(GamePlayer gp) {
        return gp.getRole().passive(PASSIVE_CLEARS_STUN, 0) > 0;
    }

    // ---- active abilities (F key) ----

    /** Action-bar text for the F-key ability: READY, seconds left, or a dash if the role has none. */
    public String abilityStatus(Player player, GamePlayer gp) {
        if (!gp.getRole().hasAbility()) return "<gray>-";
        UUID id = player.getUniqueId();
        if (abilityCooldowns.isReady(id)) return "<green>READY";
        return "<red>" + abilityCooldowns.remainingSeconds(id) + "s";
    }

    public void activate(Player player, GamePlayer gp) {
        AbilitySpec spec = gp.getRole().ability();
        if (spec == null) {
            player.sendActionBar(plugin.getMessageService().get(player, "actionbar.no-ability"));
            return;
        }
        RoleAbility ability = plugin.getAbilityRegistry().get(spec.id());
        if (ability == null) {
            plugin.getLogger().warning("Role " + gp.getRole().id() + " uses unknown ability '" + spec.id() + "'");
            return;
        }

        UUID id = player.getUniqueId();
        if (!abilityCooldowns.isReady(id)) {
            player.sendActionBar(plugin.getMessageService().get(player, "actionbar.ability-cooldown", "seconds", abilityCooldowns.remainingSeconds(id)));
            return;
        }
        ability.activate(new AbilityContext(plugin, game, player, gp, spec));
        abilityCooldowns.set(id, spec.cooldownSeconds());

        String custom = spec.text("message", null);
        player.sendActionBar(custom != null ? miniMessage.deserialize(custom)
                : plugin.getMessageService().get(player, ability.defaultMessageKey(),
                "seconds", (int) spec.number("duration-seconds", 0)));
    }

    /** Breaks invisibility early on attack or loot pickup. */
    public void breakInvisibility(Player player) {
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }
}
