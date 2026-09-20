package me.psikuvit.copperHeist.cosmetics;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.CosmeticEquipEvent;
import me.psikuvit.copperHeist.event.CosmeticUnlockedEvent;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import me.psikuvit.copperHeist.progress.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Everything the game does with cosmetics, in one place: who owns what, buying with coins, equipping, and playing what a player has
 * equipped when its moment comes. Menus, commands and gameplay hooks only call this - none of them know the rules. Ownership is kept
 * in the player's {@link PlayerProfile}; coins go through {@link ProgressService}. Main thread only.
 */
public class CosmeticService {

    /** The result of buying, granting or equipping; menus turn each into a message. */
    public enum Outcome {
        OK,
        ALREADY_HAVE,
        NOT_FOR_SALE,
        LEVEL_TOO_LOW,
        NOT_ENOUGH_COINS,
        NOT_AVAILABLE,
        CANCELLED,
        NOT_LOADED,
        DISABLED
    }

    private static final String FLAG_EFFECTS_OFF = "cosmetics-off";

    private final CopperHeist plugin;
    private final CosmeticRegistry registry;
    private final EffectRegistry effects;

    public CosmeticService(CopperHeist plugin, CosmeticRegistry registry, EffectRegistry effects) {
        this.plugin = plugin;
        this.registry = registry;
        this.effects = effects;
    }

    public CosmeticRegistry registry() {
        return registry;
    }

    public EffectRegistry effects() {
        return effects;
    }

    /** False when cosmetics are switched off (features.cosmetics) or what they need - the database, profiles, progression - is missing. */
    public boolean enabled() {
        ProgressService progress = plugin.getProgress();
        return plugin.settings().getBoolean("features.cosmetics", true) && plugin.getProfiles() != null
                && progress != null && progress.enabled();
    }

    // ---- what a player has ----

    private PlayerProfile profile(Player player) {
        return plugin.getProfiles() == null ? null : plugin.getProfiles().get(player);
    }

    private int level(Player player) {
        return plugin.getProgress().level(player.getUniqueId(), player.getName());
    }

    private boolean permitted(Player player, CosmeticDefinition cosmetic) {
        return player.hasPermission(cosmetic.idPermission()) || player.hasPermission("copperheist.cosmetic.*")
                || (cosmetic.permission() != null && player.hasPermission(cosmetic.permission()));
    }

    /** Whether the player may equip this: bought or granted, free at their level, or covered by a permission. */
    public boolean hasAccess(Player player, CosmeticDefinition cosmetic) {
        if (!enabled()) return false;
        PlayerProfile profile = profile(player);
        return CosmeticRules.hasAccess(cosmetic, profile != null && profile.owns(cosmetic.id()), permitted(player, cosmetic), level(player));
    }

    /** What the player can see in a category's menu, in file order (hidden ones only once they have them). */
    public List<CosmeticDefinition> visibleIn(Player player, CosmeticCategory category) {
        List<CosmeticDefinition> shown = new ArrayList<>();
        for (CosmeticDefinition cosmetic : registry.inCategory(category)) {
            if (CosmeticRules.visible(cosmetic, hasAccess(player, cosmetic))) shown.add(cosmetic);
        }
        return shown;
    }

    /** The item equipped in a category, or null (also null if they no longer have access to it, e.g. a permission was removed). */
    public CosmeticDefinition equipped(Player player, CosmeticCategory category) {
        PlayerProfile profile = profile(player);
        if (profile == null || !enabled()) return null;
        CosmeticDefinition cosmetic = registry.get(profile.equipped(category.id()));
        return cosmetic != null && hasAccess(player, cosmetic) ? cosmetic : null;
    }

    // ---- buying, granting, equipping ----

    /** What would happen if the player tried to buy it now - for showing the right lore without changing anything. */
    public Outcome preview(Player player, CosmeticDefinition cosmetic) {
        if (!enabled()) return Outcome.DISABLED;
        if (profile(player) == null) return Outcome.NOT_LOADED;
        var check = CosmeticRules.canBuy(cosmetic, hasAccess(player, cosmetic), level(player),
                plugin.getProgress().coins(player.getUniqueId(), player.getName()));
        return switch (check) {
            case OK -> Outcome.OK;
            case ALREADY_HAVE -> Outcome.ALREADY_HAVE;
            case NOT_FOR_SALE -> Outcome.NOT_FOR_SALE;
            case LEVEL_TOO_LOW -> Outcome.LEVEL_TOO_LOW;
            case NOT_ENOUGH_COINS -> Outcome.NOT_ENOUGH_COINS;
        };
    }

    /** Buys the cosmetic with coins: checks the rules, takes the coins, records ownership and saves it straight away. */
    public Outcome purchase(Player player, CosmeticDefinition cosmetic) {
        Outcome outcome = preview(player, cosmetic);
        if (outcome != Outcome.OK) return outcome;
        ProgressService progress = plugin.getProgress();
        if (!progress.spend(player.getUniqueId(), player.getName(), cosmetic.price(), "purchase")) return Outcome.NOT_ENOUGH_COINS;

        PlayerProfile profile = profile(player);
        if (!profile.unlock(cosmetic.id())) {
            progress.refundCoins(player.getUniqueId(), player.getName(), cosmetic.price());
            return Outcome.ALREADY_HAVE;
        }
        plugin.getProfiles().save(profile);
        Bukkit.getPluginManager().callEvent(new CosmeticUnlockedEvent(player, cosmetic, "purchase"));
        return Outcome.OK;
    }

    /** Gives the cosmetic for free (admin command, quest reward, another plugin). */
    public Outcome grant(Player player, CosmeticDefinition cosmetic, String reason) {
        if (!enabled()) return Outcome.DISABLED;
        PlayerProfile profile = profile(player);
        if (profile == null) return Outcome.NOT_LOADED;
        if (!profile.unlock(cosmetic.id())) return Outcome.ALREADY_HAVE;
        plugin.getProfiles().save(profile);
        Bukkit.getPluginManager().callEvent(new CosmeticUnlockedEvent(player, cosmetic, reason));
        return Outcome.OK;
    }

    /** Takes a cosmetic away (also unequips it). */
    public Outcome take(Player player, CosmeticDefinition cosmetic) {
        if (!enabled()) return Outcome.DISABLED;
        PlayerProfile profile = profile(player);
        if (profile == null) return Outcome.NOT_LOADED;
        if (!profile.revoke(cosmetic.id())) return Outcome.NOT_AVAILABLE;
        plugin.getProfiles().save(profile);
        return Outcome.OK;
    }

    /** Equips a cosmetic the player has access to, replacing what was in that category. Plugins can cancel it. */
    public Outcome equip(Player player, CosmeticDefinition cosmetic) {
        if (!enabled()) return Outcome.DISABLED;
        PlayerProfile profile = profile(player);
        if (profile == null) return Outcome.NOT_LOADED;
        if (!hasAccess(player, cosmetic)) return Outcome.NOT_AVAILABLE;

        CosmeticEquipEvent event = new CosmeticEquipEvent(player, cosmetic);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return Outcome.CANCELLED;

        profile.unlock(cosmetic.id()); // free and permission-granted ones are recorded the first time they are used
        profile.equip(cosmetic.category().id(), cosmetic.id());
        plugin.getProfiles().save(profile);
        return Outcome.OK;
    }

    public void unequip(Player player, CosmeticCategory category) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        profile.unequip(category.id());
        plugin.getProfiles().save(profile);
    }

    // ---- the player's own switch ----

    /** A player can turn all cosmetic effects off for themselves (what they see, and what they show others). */
    public boolean effectsEnabled(Player player) {
        PlayerProfile profile = profile(player);
        return profile == null || !profile.flag(FLAG_EFFECTS_OFF);
    }

    public void setEffectsEnabled(Player player, boolean on) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        profile.setFlag(FLAG_EFFECTS_OFF, !on);
        plugin.getProfiles().save(profile);
    }

    // ---- playing ----

    /**
     * Plays whatever {@code owner} has equipped in {@code category}, to {@code viewers} (anyone who turned effects off is skipped).
     * Does nothing when cosmetics are off, nothing is equipped, or the effect is gone. A broken effect is logged, never thrown.
     */
    public void play(Player owner, CosmeticCategory category, Location location, Entity entity, Collection<Player> viewers) {
        if (!enabled() || !effectsEnabled(owner)) return;
        CosmeticDefinition cosmetic = equipped(owner, category);
        if (cosmetic == null || cosmetic.effect() == null) return;
        EffectProvider provider = effects.get(cosmetic.effect());
        if (provider == null) return;

        List<Player> audience = new ArrayList<>();
        for (Player viewer : viewers) {
            if (effectsEnabled(viewer)) audience.add(viewer);
        }
        if (audience.isEmpty()) return;
        try {
            provider.play(new EffectContext(cosmetic, owner, location, entity, audience));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Cosmetic effect '" + cosmetic.effect() + "' failed for " + cosmetic.id(), ex);
        }
    }

    /** The equipped title's MiniMessage text (for chat and the scoreboard), or null. */
    public String title(Player player) {
        CosmeticDefinition cosmetic = equipped(player, CosmeticCategory.TITLE);
        return cosmetic == null ? null : cosmetic.name();
    }
}
