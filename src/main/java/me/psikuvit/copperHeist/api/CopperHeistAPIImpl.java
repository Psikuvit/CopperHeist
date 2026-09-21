package me.psikuvit.copperHeist.api;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.ArenaResetStrategy;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.loot.visual.LootBagVisual;
import me.psikuvit.copperHeist.menu.MenuProvider;
import me.psikuvit.copperHeist.npc.NpcProvider;
import me.psikuvit.copperHeist.party.PartyProvider;
import me.psikuvit.copperHeist.respawn.RespawnProvider;
import me.psikuvit.copperHeist.role.ability.RoleAbility;
import me.psikuvit.copperHeist.shop.action.ShopAction;
import me.psikuvit.copperHeist.stats.PlayerStats;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsService;
import me.psikuvit.copperHeist.stats.TopEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class CopperHeistAPIImpl implements CopperHeistAPI {

    private final CopperHeist plugin;

    public CopperHeistAPIImpl(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public GameView getGame(Player player) {
        Game game = plugin.getGameManager().getGame(player);
        return game == null ? null : new GameView(game);
    }

    @Override
    public Collection<GameView> getGames() {
        List<GameView> views = new ArrayList<>();
        for (Game game : plugin.getGameManager().all()) views.add(new GameView(game));
        return views;
    }

    @Override
    public boolean statsAvailable() {
        return plugin.getStats() != null;
    }

    @Override
    public CompletableFuture<PlayerStats> getStats(UUID player) {
        StatsService stats = plugin.getStats();
        if (stats == null) return CompletableFuture.completedFuture(null);
        if (stats.isLoaded(player)) {
            OfflinePlayer known = Bukkit.getOfflinePlayer(player);
            return CompletableFuture.completedFuture(stats.snapshot(player, known.getName() == null ? player.toString() : known.getName()));
        }
        OfflinePlayer known = Bukkit.getOfflinePlayer(player);
        String name = known.getName() == null ? player.toString() : known.getName();
        return stats.repository().load(player).thenApply(values -> new PlayerStats(player, name, values));
    }

    @Override
    public List<TopEntry> getTop(Stat stat) {
        return plugin.getLeaderboards() == null ? List.of() : plugin.getLeaderboards().top(stat);
    }

    @Override
    public void registerShopAction(String id, ShopAction action) {
        plugin.getShopActions().register(id, action);
    }

    @Override
    public void registerRoleAbility(String id, RoleAbility ability) {
        plugin.getAbilityRegistry().register(id, ability);
    }

    @Override
    public void registerNpcProvider(String id, Supplier<NpcProvider> factory) {
        plugin.providers().npc().register(id, factory);
    }

    @Override
    public void registerPartyProvider(String id, Supplier<PartyProvider> factory) {
        plugin.providers().party().register(id, factory);
    }

    @Override
    public void registerMenuProvider(String id, Supplier<MenuProvider> factory) {
        plugin.providers().menu().register(id, factory);
    }

    @Override
    public void registerRespawnProvider(String id, Supplier<RespawnProvider> factory) {
        plugin.providers().respawn().register(id, factory);
    }

    @Override
    public void registerLootBagVisual(String id, Supplier<LootBagVisual> factory) {
        plugin.providers().lootBagVisual().register(id, factory);
    }

    @Override
    public void registerResetStrategy(String id, Supplier<ArenaResetStrategy> factory) {
        plugin.providers().reset().register(id, factory);
    }

    @Override
    public void registerCosmeticEffect(EffectProvider effect) {
        plugin.getCosmeticEffects().register(effect);
    }
}
