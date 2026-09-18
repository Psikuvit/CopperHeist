package me.psikuvit.copperHeist.npc;

/** One way of physically representing the shop/role NPC. Returns null to place nothing. */
public interface NpcProvider {

    NpcHandle spawn(NpcSpec spec);
}
