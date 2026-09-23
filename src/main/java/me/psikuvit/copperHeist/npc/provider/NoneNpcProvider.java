package me.psikuvit.copperHeist.npc.provider;

import me.psikuvit.copperHeist.npc.NpcHandle;
import me.psikuvit.copperHeist.npc.NpcProvider;
import me.psikuvit.copperHeist.npc.NpcSpec;

/** No NPC at all - players use /ch shop and /ch role instead. */
public class NoneNpcProvider implements NpcProvider {

    @Override
    public NpcHandle spawn(NpcSpec spec) {
        return null;
    }
}
