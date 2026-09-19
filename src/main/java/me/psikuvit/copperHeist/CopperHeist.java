package me.psikuvit.copperHeist;

import me.psikuvit.copperHeist.api.CopperHeistAPI;
import me.psikuvit.copperHeist.api.CopperHeistAPIImpl;
import me.psikuvit.copperHeist.arena.ArenaManager;
import me.psikuvit.copperHeist.arena.WorldRules;
import me.psikuvit.copperHeist.arena.ArenaResetter;
import me.psikuvit.copperHeist.command.CopperHeistCommand;
import me.psikuvit.copperHeist.command.Msg;
import me.psikuvit.copperHeist.config.ConfigMigrator;
import me.psikuvit.copperHeist.config.PresetRegistry;
import me.psikuvit.copperHeist.hook.HookManager;
import me.psikuvit.copperHeist.network.NetworkService;
import me.psikuvit.copperHeist.config.Settings;
import me.psikuvit.copperHeist.database.Database;
import me.psikuvit.copperHeist.database.MysqlDatabase;
import me.psikuvit.copperHeist.database.SqliteDatabase;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.listener.StatsListener;
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
import me.psikuvit.copperHeist.listener.VaultRegionListener;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.provider.Providers;
import me.psikuvit.copperHeist.loot.LootTierRegistry;
import me.psikuvit.copperHeist.loot.LootWeightService;
import me.psikuvit.copperHeist.shop.ShopService;
import me.psikuvit.copperHeist.stats.LeaderboardService;
import me.psikuvit.copperHeist.stats.StatsRepository;
import me.psikuvit.copperHeist.stats.StatsService;
import me.psikuvit.copperHeist.shop.action.ShopActionRegistry;
import me.psikuvit.copperHeist.ui.LobbyKitService;
import me.psikuvit.copperHeist.ui.MessageService;
import me.psikuvit.copperHeist.ui.SidebarService;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;

public final class CopperHeist extends JavaPlugin {

    private ArenaManager arenaManager;
    private final WorldRules worldRules = new WorldRules(this);
    private ArenaResetter arenaResetter;
    private GameManager gameManager;
    private MessageService messageService;
    private SidebarService sidebarService;
    private LootWeightService lootWeightService;
    private LobbyKitService lobbyKitService;
    private ShopService shopService;
    private Settings settings;
    private PresetRegistry presets;
    private RoleRegistry roleRegistry;
    private LootTierRegistry lootTiers;
    private AbilityRegistry abilityRegistry;
    private ShopActionRegistry shopActions;
    private Providers providers;
    private StatsService statsService;
    private LeaderboardService leaderboards;
    private NetworkService network;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrate(this);
        settings = Settings.of(this::getConfig);
        Team.configure(getConfig().getConfigurationSection("teams"));

        PdcKeys.init(this);
        DeliveryGoal.init(this);

        presets = new PresetRegistry(this);
        presets.load();
        arenaManager = new ArenaManager(this);
        arenaResetter = new ArenaResetter();
        providers = new Providers(this);
        gameManager = new GameManager(this);
        messageService = new MessageService(this);
        messageService.load();
        Msg.init(messageService);
        sidebarService = new SidebarService(this);
        sidebarService.load();
        sidebarService.startHub();
        lootWeightService = new LootWeightService(this);
        lobbyKitService = new LobbyKitService(this);
        lobbyKitService.load();
        shopActions = new ShopActionRegistry();
        shopService = new ShopService(this);
        shopService.load();
        lootTiers = new LootTierRegistry(this);
        lootTiers.load();
        LootItem.init(lootTiers);
        abilityRegistry = new AbilityRegistry();
        roleRegistry = new RoleRegistry(this);
        roleRegistry.load();
        startStats();

        network = new NetworkService(this);
        network.start();

        arenaManager.loadAll();
        arenaManager.all().forEach(arena -> providers.reset().resolve(settings.getString("reset.method", "entities")).reset(arena));

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
        getServer().getPluginManager().registerEvents(new VaultRegionListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);

        HookManager.enable(this);
        CopperHeistCommand.register(this);
        getServer().getServicesManager().register(CopperHeistAPI.class, new CopperHeistAPIImpl(this), this, ServicePriority.Normal);

        getLogger().info("Copper Heist enabled - " + arenaManager.all().size() + " arena(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.shutdownAll();
        if (network != null) network.shutdown();
        if (leaderboards != null) leaderboards.stop();
        if (statsService != null) statsService.shutdown();
    }

    /** Opens the player-data database and starts the stats service; a failure only disables stats, never the plugin. */
    private void startStats() {
        String type = settings.getString("database.type", "sqlite");
        if (!settings.getBoolean("stats.enabled", true) || "none".equalsIgnoreCase(type)) {
            getLogger().info("Stats are disabled (stats.enabled / database.type).");
            return;
        }
        Database database = openDatabase(type);
        if (database == null) {
            getLogger().warning("Unknown database.type '" + type + "' (use sqlite, mysql or none) - stats are off.");
            return;
        }
        try {
            database.createTables();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Could not open the " + type + " database - stats and leaderboards are off. "
                    + "Check the database section of config.yml.", ex);
            return;
        }
        statsService = new StatsService(this, new StatsRepository(database));
        statsService.start();
        leaderboards = new LeaderboardService(this, statsService.repository());
        leaderboards.start();
        getLogger().info("Player stats connected (" + type.toLowerCase() + ").");
    }

    private Database openDatabase(String type) {
        if ("sqlite".equalsIgnoreCase(type)) {
            return new SqliteDatabase(new File(getDataFolder(), settings.getString("database.sqlite.file", "data.db")));
        }
        if ("mysql".equalsIgnoreCase(type)) {
            return new MysqlDatabase(settings.getString("database.mysql.host", "localhost"),
                    settings.getInt("database.mysql.port", 3306), settings.getString("database.mysql.database", "copperheist"),
                    settings.getString("database.mysql.user", "root"), settings.getString("database.mysql.password", ""),
                    settings.getBoolean("database.mysql.use-ssl", false));
        }
        return null;
    }

    /** The stats service, or null if stats are disabled or the database couldn't be opened. */
    public NetworkService getNetwork() {
        return network;
    }

    public LeaderboardService getLeaderboards() {
        return leaderboards;
    }

    public StatsService getStats() {
        return statsService;
    }

    /** Live, layered view of config.yml - prefer this over getConfig() so overrides and presets apply. */
    public Settings settings() {
        return settings;
    }

    public LootTierRegistry getLootTiers() {
        return lootTiers;
    }

    public RoleRegistry getRoleRegistry() {
        return roleRegistry;
    }

    public AbilityRegistry getAbilityRegistry() {
        return abilityRegistry;
    }

    public PresetRegistry getPresets() {
        return presets;
    }

    public WorldRules getWorldRules() {
        return worldRules;
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

    public Providers providers() {
        return providers;
    }

    public ShopActionRegistry getShopActions() {
        return shopActions;
    }

    public ShopService getShopService() {
        return shopService;
    }
}
