package me.psikuvit.copperHeist.command;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Team;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * /ch setup &lt;arena&gt;: the whole arena checklist in chat. Each line shows whether that step is done and a
 * clickable [set] that runs the matching /ch arena command from where the admin is standing (or looking).
 */
public final class SetupWizard {

    private record Step(String labelKey, Predicate<Arena> done, String command, Object... labelArgs) {
    }

    private final CopperHeist plugin;

    public SetupWizard(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void show(CommandSender sender, Arena arena) {
        String name = arena.getName();
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("wizard.step.lobby", a -> a.getLobby() != null, "/ch arena setlobby " + name));
        steps.add(new Step("wizard.step.spectator", a -> a.getSpectator() != null, "/ch arena setspectator " + name));
        steps.add(new Step("wizard.step.bounds1", a -> a.getBound1() != null, "/ch arena setbounds1 " + name));
        steps.add(new Step("wizard.step.bounds2", a -> a.getBound2() != null, "/ch arena setbounds2 " + name));
        steps.add(new Step("wizard.step.loot", a -> a.allLootPoints().size() >= 6, "/ch arena addloot " + name));
        steps.add(new Step("wizard.step.relic", a -> !a.getRelicPoints().isEmpty(), "/ch arena addrelic " + name));

        for (Team team : Team.values()) {
            String id = team.name().toLowerCase(Locale.ROOT);
            String suffix = " " + name + " " + id;
            Object[] label = {"team", team.displayName()};
            steps.add(new Step("wizard.step.spawn", a -> a.site(team).spawn != null, "/ch arena setspawn" + suffix, label));
            steps.add(new Step("wizard.step.dock", a -> !a.site(team).dockChests.isEmpty(), "/ch arena adddock" + suffix, label));
            steps.add(new Step("wizard.step.vault-chest", a -> !a.site(team).vaultChests.isEmpty(), "/ch arena addvaultchest" + suffix, label));
            steps.add(new Step("wizard.step.vault-door", a -> a.site(team).vaultDoor != null, "/ch arena setvaultdoor" + suffix, label));
            steps.add(new Step("wizard.step.golem-idle", a -> a.site(team).golemIdle != null, "/ch arena setgolemidle" + suffix, label));
            steps.add(new Step("wizard.step.waypoint", a -> !a.site(team).waypoints.isEmpty(), "/ch arena addwaypoint" + suffix, label));
            steps.add(new Step("wizard.step.shop", a -> a.site(team).shop != null, "/ch arena setshop" + suffix, label));
            steps.add(new Step("wizard.step.base", a -> a.site(team).base() != null, "/ch arena setbase1" + suffix, label));
            steps.add(new Step("wizard.step.vault-region", a -> a.site(team).vaultRegion() != null, "/ch arena setvaultregion1" + suffix, label));
        }
        steps.add(new Step("wizard.step.snapshot", a -> plugin.getArenaManager().snapshotFile(a).exists(), "/ch arena snapshot " + name));

        int done = 0;
        for (Step step : steps) if (step.done().test(arena)) done++;
        Msg.info(sender, "wizard.header", "arena", name, "done", done, "total", steps.size());
        for (Step step : steps) {
            boolean ok = step.done().test(arena);
            Object[] args = new Object[step.labelArgs().length + 6];
            System.arraycopy(step.labelArgs(), 0, args, 0, step.labelArgs().length);
            int at = step.labelArgs().length;
            args[at] = "mark";
            args[at + 1] = Msg.word(sender, ok ? "wizard.done" : "wizard.todo");
            args[at + 2] = "label";
            args[at + 3] = Msg.word(sender, step.labelKey(), step.labelArgs());
            args[at + 4] = "command";
            args[at + 5] = step.command();
            Msg.info(sender, "wizard.line", args);
        }
        Msg.info(sender, "wizard.footer", "validate", "/ch arena validate " + name, "enable", "/ch arena enable " + name);
    }
}
