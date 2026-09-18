package me.psikuvit.copperHeist;

import me.psikuvit.copperHeist.arena.ArenaManager;
import me.psikuvit.copperHeist.arena.ArenaResetter;
import me.psikuvit.copperHeist.command.CopperHeistCommand;
import me.psikuvit.copperHeist.config.ConfigMigrator;
import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.role.RoleRegistry;
import me.psikuvit.copperHeist.role.ability.AbilityRegistry;
import me.psikuvit.copperHeist.game.GameManager;
import me.psikuvit.copperHeist.golem.DeliveryGoal;
import me.psikuvit.copperHeist.listener.ArenaProtectionListener;
import me.psikuvit.copperHeist.listener.CombatListener;
import me.psikuvit.copperHeist.listener.GameEventListener;
import me.psikuvit.copperHeist.listener.GolemInteractListener;
import me.psikuvit.copperHeist.listener.GustPadListener;
import me.psikuvit.copperHeist.listener.HeistListener;
import me.psikuvit.copperHeist.listener.LobbyListener;
import me.psikuvit.copperHeist.listener.LootListener;
import me.psikuvit.copperHeist.listener.PlayerConnectionListener;
import me.psikuvit.copperHeist.listener.RoleListener;
import me.psikuvit.copperHeist.listener.ShopListener;
import me.psikuvit.copperHeist.loot.LootWeightService;
import me.psikuvit.copperHeist.shop.ShopService;
import me.psikuvit.copperHeist.ui.LobbyKitService;
import me.psikuvit.copperHeist.ui.MessageService;
import me.psikuvit.copperHeist.ui.SidebarService;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.plugin.java.JavaPlugin;

public final class CopperHeist extends JavaPlugin {

    private ArenaManager arenaManager;
    private ArenaResetter arenaResetter;
    private GameManager gameManager;
    private MessageService messageService;
    private SidebarService sidebarService;
    private LootWeightService lootWeightService;
    private LobbyKitService lobbyKitService;
    private ShopService shopService;
    private Settings settings;
    private RoleRegistry roleRegistry;
    private AbilityRegistry abilityRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrate(this);
        settings = Settings.of(this::getConfig);
        Team.configure(getConfig().getConfigurationSection("teams"));

        PdcKeys.init(this);
        DeliveryGoal.init(this);

        arenaManager = new ArenaManager(this);
        arenaResetter = new ArenaResetter();
        gameManager = new GameManager(this);
        messageService = new MessageService(this);
        messageService.load();
        sidebarService = new SidebarService(this);
        sidebarService.load();
        sidebarService.startHub();
        lootWeightService = new LootWeightService(this);
        lobbyKitService = new LobbyKitService(this);
        lobbyKitService.load();
        shopService = new ShopService(this);
        shopService.load();
        abilityRegistry = new AbilityRegistry();
        roleRegistry = new RoleRegistry(this);
        roleRegistry.load();

        arenaManager.loadAll();
        arenaManager.all().forEach(arenaResetter::reset);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new LootListener(this), this);
        getServer().getPluginManager().registerEvents(new GolemInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new RoleListener(this), this);
        getServer().getPluginManager().registerEvents(new GameEventListener(this), this);
        getServer().getPluginManager().registerEvents(new HeistListener(this), this);
        getServer().getPluginManager().registerEvents(new GustPadListener(this), this);

        CopperHeistCommand.register(this);

        getLogger().info("Copper Heist enabled - " + arenaManager.all().size() + " arena(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (gameManager == null) return;
        gameManager.shutdownAll();
    }

    /** Live, layered view of config.yml - prefer this over getConfig() so overrides and presets apply. */
    public Settings settings() {
        return settings;
    }

    public RoleRegistry getRoleRegistry() {
        return roleRegistry;
    }

    public AbilityRegistry getAbilityRegistry() {
        return abilityRegistry;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ArenaResetter getArenaResetter() {
        return arenaResetter;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public SidebarService getSidebarService() {
        return sidebarService;
    }

    public LootWeightService getLootWeightService() {
        return lootWeightService;
    }

    public LobbyKitService getLobbyKitService() {
        return lobbyKitService;
    }

    public ShopService getShopService() {
        return shopService;
    }
}
