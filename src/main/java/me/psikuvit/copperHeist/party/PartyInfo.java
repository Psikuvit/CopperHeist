package me.psikuvit.copperHeist.party;

import java.util.List;
import java.util.UUID;

/** A party as the match code sees it: who leads it and who is in it (the leader included), whichever plugin manages it. */
public record PartyInfo(UUID leader, List<UUID> members) {

    public PartyInfo {
        members = List.copyOf(members);
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }
}
