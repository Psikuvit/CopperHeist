package me.psikuvit.copperHeist.api;

import me.psikuvit.copperHeist.arena.ArenaResetStrategy;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import me.psikuvit.copperHeist.loot.visual.LootBagVisual;
import me.psikuvit.copperHeist.menu.MenuProvider;
import me.psikuvit.copperHeist.npc.NpcProvider;
import me.psikuvit.copperHeist.party.PartyProvider;
import me.psikuvit.copperHeist.respawn.RespawnProvider;
import me.psikuvit.copperHeist.role.ability.RoleAbility;
import me.psikuvit.copperHeist.shop.action.ShopAction;
import me.psikuvit.copperHeist.stats.PlayerStats;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.TopEntry;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The public face of Copper Heist for other plugins. Fetch it with
 * {@code Bukkit.getServicesManager().load(CopperHeistAPI.class)}.
 *
 * <p>Games are exposed as read-only {@link GameView}s. Custom content is added by registering it under an id and
 * then selecting that id in config.yml (a shop entry's {@code action}, a role's {@code ability}, or the
 * {@code npc.type} / {@code ui.menu} / {@code respawn.mode} / {@code loot.bag.visual} / {@code reset.method} keys).
 * Register during your plugin's onEnable; Copper Heist loads after it when you list it as a dependency.
 *
 * <p>Custom events ({@code LootDeliveredEvent}, {@code MatchEndEvent}, {@code PhaseChangeEvent} ...) live in the
 * {@code me.psikuvit.copperHeist.event} package.
 */
public interface CopperHeistAPI {

    /** The match the player is in (playing or spectating), or null. */
    @Nullable GameView getGame(Player player);

    /** One view per arena that currently has a game object (idle arenas included). */
    Collection<GameView> getGames();

    /** True if stats are enabled and their database opened. */
    boolean statsAvailable();

    /** A player's stored stats (all zero if they never played); completes with null when stats are disabled. */
    CompletableFuture<@Nullable PlayerStats> getStats(UUID player);

    /** The best players for a stat as of the last leaderboard refresh - never touches the database. */
    List<TopEntry> getTop(Stat stat);

    void registerShopAction(String id, ShopAction action);

    void registerRoleAbility(String id, RoleAbility ability);

    void registerNpcProvider(String id, Supplier<NpcProvider> factory);

    void registerMenuProvider(String id, Supplier<MenuProvider> factory);

    void registerRespawnProvider(String id, Supplier<RespawnProvider> factory);

    void registerLootBagVisual(String id, Supplier<LootBagVisual> factory);

    void registerResetStrategy(String id, Supplier<ArenaResetStrategy> factory);

    /**
     * Adds a source of parties (another party plugin's bridge). Server owners select it with {@code party.provider: <id>}; the match code then
     * seats that plugin's parties together and the built-in /party commands step aside.
     */
    void registerPartyProvider(String id, Supplier<PartyProvider> factory);

    /**
     * Adds an effect that cosmetics.yml entries can use through {@code effect: <id>}. Register it in your onEnable (cosmetics.yml is read
     * after every plugin has enabled). Effects must be purely visual.
     */
    void registerCosmeticEffect(EffectProvider effect);
}
