package me.psikuvit.copperHeist;

import me.psikuvit.copperHeist.arena.ArenaManager;
import me.psikuvit.copperHeist.arena.ArenaResetter;
import me.psikuvit.copperHeist.command.CopperHeistCommand;
import me.psikuvit.copperHeist.game.GameManager;
import me.psikuvit.copperHeist.golem.DeliveryGoal;
import me.psikuvit.copperHeist.listener.ArenaProtectionListener;
import me.psikuvit.copperHeist.listener.CombatListener;
import me.psikuvit.copperHeist.listener.GolemInteractListener;
import me.psikuvit.copperHeist.listener.LobbyListener;
import me.psikuvit.copperHeist.listener.LootListener;
import me.psikuvit.copperHeist.listener.PlayerConnectionListener;
import me.psikuvit.copperHeist.loot.LootWeightService;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

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

        arenaManager.loadAll();

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new LootListener(this), this);
        getServer().getPluginManager().registerEvents(new GolemInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);

        CopperHeistCommand.register(this);

        getLogger().info("Copper Heist enabled - " + arenaManager.all().size() + " arena(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (gameManager == null) return;
        for (var game : gameManager.all()) {
            game.forceStop();
        }
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
}
