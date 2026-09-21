package me.psikuvit.copperHeist.provider;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.ArenaResetStrategy;
import me.psikuvit.copperHeist.arena.SnapshotResetStrategy;
import me.psikuvit.copperHeist.loot.visual.BlockDisplayVisual;
import me.psikuvit.copperHeist.loot.visual.ItemDisplayVisual;
import me.psikuvit.copperHeist.loot.visual.LabelOnlyVisual;
import me.psikuvit.copperHeist.loot.visual.LootBagVisual;
import me.psikuvit.copperHeist.menu.ChestMenuProvider;
import me.psikuvit.copperHeist.menu.DialogMenuProvider;
import me.psikuvit.copperHeist.menu.MenuProvider;
import me.psikuvit.copperHeist.npc.ArmorStandNpcProvider;
import me.psikuvit.copperHeist.npc.InteractionNpcProvider;
import me.psikuvit.copperHeist.npc.MannequinNpcProvider;
import me.psikuvit.copperHeist.npc.NoneNpcProvider;
import me.psikuvit.copperHeist.npc.NpcProvider;
import me.psikuvit.copperHeist.npc.VillagerNpcProvider;
import me.psikuvit.copperHeist.hook.party.PartiesHook;
import me.psikuvit.copperHeist.party.BuiltInPartyProvider;
import me.psikuvit.copperHeist.party.NonePartyProvider;
import me.psikuvit.copperHeist.party.PartyProvider;
import me.psikuvit.copperHeist.respawn.GhostRespawnProvider;
import me.psikuvit.copperHeist.respawn.InstantRespawnProvider;
import me.psikuvit.copperHeist.respawn.RespawnProvider;
import me.psikuvit.copperHeist.respawn.SpectatorWaitRespawnProvider;

/**
 * Every "pick one" feature in one place. Each registry ships its built-in choices; other plugins
 * (and later phases, e.g. the WorldEdit hook) register more, and config.yml selects by id.
 */
public final class Providers {

    private final ProviderRegistry<NpcProvider> npc;
    private final ProviderRegistry<MenuProvider> menu;
    private final ProviderRegistry<LootBagVisual> lootBagVisual;
    private final ProviderRegistry<RespawnProvider> respawn;
    private final ProviderRegistry<ArenaResetStrategy> reset;
    private final ProviderRegistry<PartyProvider> party;

    public Providers(CopperHeist plugin) {
        var log = plugin.getLogger();

        npc = new ProviderRegistry<>("npc.type", "villager", log);
        npc.register("mannequin", MannequinNpcProvider::new);
        npc.register("villager", VillagerNpcProvider::new);
        npc.register("armor-stand", ArmorStandNpcProvider::new);
        npc.register("interaction", InteractionNpcProvider::new);
        npc.register("none", NoneNpcProvider::new);

        menu = new ProviderRegistry<>("ui.menu", "chest", log);
        menu.register("chest", () -> new ChestMenuProvider(plugin));
        menu.register("dialog", () -> new DialogMenuProvider(plugin));

        lootBagVisual = new ProviderRegistry<>("loot.bag.visual", "item-display", log);
        lootBagVisual.register("item-display", () -> new ItemDisplayVisual(plugin.settings()));
        lootBagVisual.register("block-display", () -> new BlockDisplayVisual(plugin.settings()));
        lootBagVisual.register("label-only", LabelOnlyVisual::new);

        respawn = new ProviderRegistry<>("respawn.mode", "spectator-wait", log);
        respawn.register("spectator-wait", () -> new SpectatorWaitRespawnProvider(plugin));
        respawn.register("instant", InstantRespawnProvider::new);
        respawn.register("ghost", () -> new GhostRespawnProvider(plugin));

        reset = new ProviderRegistry<>("reset.method", "entities", log);
        reset.register("entities", plugin::getArenaResetter);
        reset.register("snapshot", () -> new SnapshotResetStrategy(plugin, plugin.getArenaResetter()));

        party = new ProviderRegistry<>("party.provider", "builtin", log);
        party.register("builtin", () -> new BuiltInPartyProvider(plugin.getParties().service()));
        party.register("none", NonePartyProvider::new);
        party.register("parties", () -> {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("Parties")) throw new IllegalStateException("the Parties plugin is not installed");
            return new PartiesHook();
        });
    }

    public ProviderRegistry<NpcProvider> npc() {
        return npc;
    }

    public ProviderRegistry<MenuProvider> menu() {
        return menu;
    }

    public ProviderRegistry<LootBagVisual> lootBagVisual() {
        return lootBagVisual;
    }

    public ProviderRegistry<RespawnProvider> respawn() {
        return respawn;
    }

    public ProviderRegistry<ArenaResetStrategy> reset() {
        return reset;
    }

    public ProviderRegistry<PartyProvider> party() {
        return party;
    }
}
