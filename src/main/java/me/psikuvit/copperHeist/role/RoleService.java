package me.psikuvit.copperHeist.role;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.shop.ShopItem;
import me.psikuvit.copperHeist.task.RevealEndTask;
import me.psikuvit.copperHeist.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loadouts, passives and active abilities for the five roles. A few of
 * the original perks depend on systems this build doesn't have (base
 * regions, dock lock-picking) and are adapted or dropped - see the
 * per-role methods below for exactly what changed and why. Guard's "+1
 * alarm" is real (see {@link me.psikuvit.copperHeist.heist.AlarmManager#capFor}),
 * counted directly off this team's Guards rather than tracked here.
 */
public class RoleService {

    private static final long THIEF_INVIS_SECONDS = 5;
    private static final long THIEF_COOLDOWN_SECONDS = 45;
    private static final long SABOTEUR_REVEAL_SECONDS = 10;
    private static final long SABOTEUR_COOLDOWN_SECONDS = 60;
    private static final double GUARD_DAMAGE_BONUS = 0.20;
    private static final double GUARD_RADIUS = 15.0;

    private final CopperHeist plugin;
    private final Game game;
    private final Cooldowns abilityCooldowns = new Cooldowns();

    public RoleService(CopperHeist plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    public int countOnTeam(Team team, Role role) {
        int count = 0;
        for (UUID uuid : game.getTeam(team).getMembers()) {
            GamePlayer gp = game.getGamePlayer(uuid);
            if (gp != null && gp.getRole() == role) count++;
        }
        return count;
    }

    /** Picks the first role (in doc order) that isn't already full for this team - used as the join default. */
    public Role defaultRole(Team team) {
        for (Role role : Role.values()) {
            if (countOnTeam(team, role) < 2) return role;
        }
        return Role.RUNNER;
    }

    public void giveLoadout(Player player, Role role, Team team) {
        player.getInventory().clear();
        Color teamColor = team == Team.COPPER ? Color.ORANGE : Color.SILVER;

        switch (role) {
            case RUNNER -> {
                dyeArmor(player, teamColor
                );
                player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, true, false));
            }
            case THIEF -> {
                dyeArmor(player, teamColor
                );
                player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
            }
            case MECHANIC -> {
                player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                player.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                player.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                player.getInventory().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
                player.getInventory().addItem(new ItemStack(Material.STONE_AXE));
                player.getInventory().addItem(ShopItem.createHoneycomb(), ShopItem.createHoneycomb());
            }
            case GUARD -> {
                player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                player.getInventory().addItem(new ItemStack(Material.CROSSBOW));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 8));
            }
            case SABOTEUR -> {
                dyeArmor(player, teamColor
                );
                player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                player.getInventory().addItem(ShopItem.createOxidizerSplash(), ShopItem.createOxidizerSplash());
            }
        }

        player.setGameMode(GameMode.SURVIVAL);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) player.setHealth(maxHealth.getValue());
        player.setFoodLevel(20);
        player.setFireTicks(0);
    }

    private void dyeArmor(Player player, Color color) {
        ItemStack[] pieces = {
                new ItemStack(Material.LEATHER_HELMET),
                new ItemStack(Material.LEATHER_CHESTPLATE),
                new ItemStack(Material.LEATHER_LEGGINGS),
                new ItemStack(Material.LEATHER_BOOTS)
        };
        for (ItemStack piece : pieces) {
            if (piece.getItemMeta() instanceof LeatherArmorMeta meta) {
                meta.setColor(color);
                piece.setItemMeta(meta);
            }
        }
        player.getInventory().setHelmet(pieces[0]);
        player.getInventory().setChestplate(pieces[1]);
        player.getInventory().setLeggings(pieces[2]);
        player.getInventory().setBoots(pieces[3]);
    }

    // ---- passives ----

    public double speedPenaltyMultiplier(Role role) {
        return role == Role.RUNNER ? 0.5 : 1.0;
    }

    /** Action-bar text for the F-key ability: READY, seconds left, or a dash if the role has none. */
    public String abilityStatus(Player player, GamePlayer gp) {
        if (gp.getRole() != Role.THIEF && gp.getRole() != Role.SABOTEUR) return "<gray>-";
        UUID id = player.getUniqueId();
        if (abilityCooldowns.isReady(id)) return "<green>READY";
        return "<red>" + abilityCooldowns.remainingSeconds(id) + "s";
    }

    public double lockpickMultiplier(Role role) {
        return role == Role.THIEF ? 0.5 : 1.0;
    }

    public double scrapeCooldownMultiplier(Role role) {
        return role == Role.MECHANIC ? 0.5 : 1.0;
    }

    /** Guard's "+20% damage inside own base region" adapted to a radius around the team spawn, since this build has no base-region concept. */
    public double damageMultiplier(Player attacker, GamePlayer gp) {
        if (gp.getRole() != Role.GUARD) return 1.0;
        var site = game.getArena().site(gp.getTeam());
        if (site.spawn == null || !attacker.getWorld().equals(site.spawn.getWorld())) return 1.0;
        return attacker.getLocation().distanceSquared(site.spawn) <= GUARD_RADIUS * GUARD_RADIUS
                ? 1.0 + GUARD_DAMAGE_BONUS : 1.0;
    }

    // ---- active abilities (F key) ----

    public void activate(Player player, GamePlayer gp) {
        switch (gp.getRole()) {
            case THIEF -> activateThief(player);
            case SABOTEUR -> activateSaboteur(player, gp);
            default -> player.sendActionBar(plugin.getMessageService().get("actionbar.no-ability"));
        }
    }

    private void activateThief(Player player) {
        UUID id = player.getUniqueId();
        if (!abilityCooldowns.isReady(id)) {
            player.sendActionBar(plugin.getMessageService().get("actionbar.ability-cooldown", "seconds", abilityCooldowns.remainingSeconds(id)));
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int) (THIEF_INVIS_SECONDS * 20), 0, false, false));
        abilityCooldowns.set(id, THIEF_COOLDOWN_SECONDS);
        player.sendActionBar(plugin.getMessageService().get("actionbar.thief-invisible"));
    }

    /** Breaks the Thief's invisibility early on attack or loot pickup. */
    public void breakInvisibility(Player player) {
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    /**
     * Saboteur's "see enemy golems through walls" - implemented as Glowing,
     * which (without a packet library like ProtocolLib) is visible to every
     * player, not just the Saboteur or their team. A real per-viewer-only
     * reveal isn't reachable with vanilla Bukkit API alone.
     */
    private void activateSaboteur(Player player, GamePlayer gp) {
        UUID id = player.getUniqueId();
        if (!abilityCooldowns.isReady(id)) {
            player.sendActionBar(plugin.getMessageService().get("actionbar.ability-cooldown", "seconds", abilityCooldowns.remainingSeconds(id)));
            return;
        }
        Team enemy = gp.getTeam().opposite();
        List<CopperGolem> revealed = new ArrayList<>();
        for (var golem : game.getTeam(enemy).getGolems()) {
            golem.getEntity().setGlowing(true);
            revealed.add(golem.getEntity());
        }
        abilityCooldowns.set(id, SABOTEUR_COOLDOWN_SECONDS);
        player.sendActionBar(plugin.getMessageService().get("actionbar.saboteur-reveal"));

        new RevealEndTask(revealed).runTaskLater(plugin, SABOTEUR_REVEAL_SECONDS * 20L);
    }

    public boolean canClearStun(GamePlayer gp) {
        return gp.getRole() == Role.MECHANIC;
    }
}
