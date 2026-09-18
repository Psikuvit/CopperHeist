package me.psikuvit.copperHeist.npc;

import org.bukkit.entity.Entity;

import java.util.List;

/** {@code clickable} is the entity players right-click; {@code extras} (name tags, hitboxes) are removed along with it. */
public record NpcHandle(Entity clickable, List<Entity> extras) {

    public static NpcHandle of(Entity clickable) {
        return new NpcHandle(clickable, List.of());
    }
}
