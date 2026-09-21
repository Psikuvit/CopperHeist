package me.psikuvit.copperHeist.game;

import java.util.Optional;

/** Picks the team for someone (or a whole party) joining a match. Pure logic, so it is unit-tested. */
public final class TeamAssigner {

    private TeamAssigner() {
    }

    /**
     * @param copper    players already on Copper
     * @param iron      players already on Iron
     * @param capacity  the most players one team can hold
     * @param needed    how many seats are needed together (1 for a solo player, the party size for a party)
     * @param preferred the team to favour when it has room (a party member's team), or null
     * @return the team to join, or empty when neither team has {@code needed} free seats
     */
    public static Optional<Team> choose(int copper, int iron, int capacity, int needed, Team preferred) {
        boolean copperFits = copper + needed <= capacity;
        boolean ironFits = iron + needed <= capacity;
        if (preferred == Team.COPPER && copperFits) return Optional.of(Team.COPPER);
        if (preferred == Team.IRON && ironFits) return Optional.of(Team.IRON);
        if (copperFits && ironFits) return Optional.of(copper <= iron ? Team.COPPER : Team.IRON);
        if (copperFits) return Optional.of(Team.COPPER);
        if (ironFits) return Optional.of(Team.IRON);
        return Optional.empty();
    }
}
