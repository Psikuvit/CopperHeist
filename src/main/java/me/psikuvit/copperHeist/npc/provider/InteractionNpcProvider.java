package me.psikuvit.copperHeist.npc.provider;

import me.psikuvit.copperHeist.npc.NpcHandle;
import me.psikuvit.copperHeist.npc.NpcProvider;
import me.psikuvit.copperHeist.npc.NpcSpec;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;

import java.util.List;

/**
 * No model at all: an invisible click hitbox with floating text - ideal when the arena builder has
 * already built a shop counter or statue and just needs the click target.
 */
public class InteractionNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        float width = (float) spec.settings().getDouble("npc.interaction.width", 0.9);
        float height = (float) spec.settings().getDouble("npc.interaction.height", 1.9);

        Interaction hitbox = spec.location().getWorld().spawn(spec.location(), Interaction.class, entity -> {
            entity.setInteractionWidth(width);
            entity.setInteractionHeight(height);
            });
        TextDisplay label = spec.location().getWorld().spawn(spec.location().clone().add(0, height + 0.3, 0), TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.text(spec.name());
            });
        return new NpcHandle(hitbox, List.of(label));
    }
}
