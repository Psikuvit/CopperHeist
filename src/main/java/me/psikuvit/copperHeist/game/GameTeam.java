package me.psikuvit.copperHeist.game;

import me.psikuvit.copperHeist.golem.HeistGolem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GameTeam {

    private final Team team;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final List<HeistGolem> golems = new ArrayList<>();
    private int score = 0;
    private int steals = 0;
    private long lastDeliveryMillis = System.currentTimeMillis();
    private int relicsDelivered = 0;

    public GameTeam(Team team) {
        this.team = team;
    }

    public Team getTeam() {
        return team;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public List<HeistGolem> getGolems() {
        return golems;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int amount) {
        score += amount;
    }

    public int getRelicsDelivered() {
        return relicsDelivered;
    }

    public void addRelicDelivered() {
        relicsDelivered++;
    }

    public long getLastDeliveryMillis() {
        return lastDeliveryMillis;
    }

    public void markDelivery() {
        lastDeliveryMillis = System.currentTimeMillis();
    }

    public int getSteals() {
        return steals;
    }

    public void addSteal() {
        steals++;
    }
}
