package me.psikuvit.copperHeist.role;

import net.kyori.adventure.text.format.NamedTextColor;

/** doc §8's five roles. Max 2 of each per team, enforced in RoleService. */
public enum Role {

    RUNNER("Runner", NamedTextColor.GREEN),
    THIEF("Thief", NamedTextColor.DARK_GRAY),
    MECHANIC("Mechanic", NamedTextColor.GOLD),
    GUARD("Guard", NamedTextColor.BLUE),
    SABOTEUR("Saboteur", NamedTextColor.DARK_PURPLE);

    private final String displayName;
    private final NamedTextColor color;

    Role(String displayName, NamedTextColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }
}
