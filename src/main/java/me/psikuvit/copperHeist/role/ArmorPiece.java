package me.psikuvit.copperHeist.role;

import org.bukkit.Material;

/** One worn armor slot; {@code dyeTeam} tints leather armor in the wearer's team color. */
public record ArmorPiece(Material material, boolean dyeTeam) {
}
