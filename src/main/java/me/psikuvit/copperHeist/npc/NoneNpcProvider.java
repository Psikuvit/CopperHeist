package me.psikuvit.copperHeist.npc;

/** No NPC at all - players use /ch shop and /ch role instead. */
public class NoneNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        return null;
    }
}
