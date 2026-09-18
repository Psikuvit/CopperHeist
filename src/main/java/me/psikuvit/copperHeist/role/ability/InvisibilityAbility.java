package me.psikuvit.copperHeist.role.ability;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Turns the user invisible for {@code duration-seconds}; the effect breaks early when they attack or pick up loot. */
public class InvisibilityAbility implements RoleAbility {

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        int ticks = (int) (context.spec().number("duration-seconds", 5) * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false));
    }

    @Override
    public String defaultMessageKey() {
        return "actionbar.thief-invisible";
    }
}
