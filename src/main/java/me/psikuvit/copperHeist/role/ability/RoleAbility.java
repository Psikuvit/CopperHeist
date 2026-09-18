package me.psikuvit.copperHeist.role.ability;

/** An active role ability. Implement and register through {@link AbilityRegistry} to add new ones. */
public interface RoleAbility {

    void activate(AbilityContext context);

    /** messages.yml key shown on the action bar after use, unless the role's YAML supplies its own "message". */
    String defaultMessageKey();
}
