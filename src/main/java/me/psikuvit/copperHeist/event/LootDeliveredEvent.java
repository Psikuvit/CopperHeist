package me.psikuvit.copperHeist.event;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Fired when a golem deposits a stack into its team's vault. */
public class LootDeliveredEvent extends GameEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Team team;
    private final ItemStack item;
    private final int value;

    public LootDeliveredEvent(Game game, Team team, ItemStack item, int value) {
        super(game);
        this.team = team;
        this.item = item;
        this.value = value;
    }

    public Team getTeam() {
        return team;
    }

    public ItemStack getItem() {
        return item;
    }

    public int getValue() {
        return value;
    }

    public boolean isRelic() {
        return LootItem.isRelic(item);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
