package me.psikuvit.copperHeist.game;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Team {

    COPPER("Copper", NamedTextColor.GOLD, "[C] "),
    IRON("Iron", NamedTextColor.GRAY, "[I] ");

    private final String displayName;
    private final NamedTextColor color;
    private final String prefix;

    Team(String displayName, NamedTextColor color, String prefix) {
        this.displayName = displayName;
        this.color = color;
        this.prefix = prefix;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    public String prefix() {
        return prefix;
    }

    public Team opposite() {
        return this == COPPER ? IRON : COPPER;
    }
}
