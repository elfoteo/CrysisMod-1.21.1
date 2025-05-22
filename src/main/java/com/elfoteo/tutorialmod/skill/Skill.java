// Skill.java
package com.elfoteo.tutorialmod.skill;

import java.util.Arrays;

/**
 * An enum representing every skill in the Nanosuit tree.
 * Each entry includes its branch, x/y icon path (relative to textures/gui), title,
 * description (tooltip), and which Skills are its direct parents.
 */
public enum Skill {
    // === STEALTH BRANCH ===
    SILENT_STEP(Branch.STEALTH, "nanosuit/silent_step", "Silent Step",
            "Movement makes no sound or vibrations while walking or running.",
            new Skill[]{}),
    THERMAL_DAMPENERS(Branch.STEALTH, "nanosuit/thermal_dampeners", "Thermal Dampeners",
            "Emits less heat while cloaked, reducing visibility to thermal vision modes.",
            new Skill[]{SILENT_STEP}),
    DYNAMIC_CLOAKING(Branch.STEALTH, "nanosuit/dynamic_cloaking", "Dynamic Cloaking",
            "Cloak drains 25% less energy overall.",
            new Skill[]{THERMAL_DAMPENERS}),
    PREDATORY_STRIKE(Branch.STEALTH, "nanosuit/predatory_strike", "Predatory Strike",
            "Killing a target while cloaked regenerates 10 energy.",
            new Skill[]{SILENT_STEP}),
    GHOST_KILL(Branch.STEALTH, "nanosuit/ghost_kill", "Ghost Kill",
            "Kills in stealth no longer break cloak, cloak activation cooldown in case the entity didn't die is reduced by 0.5s",
            new Skill[]{DYNAMIC_CLOAKING, PREDATORY_STRIKE}),
    GHOST_TITAN(Branch.STEALTH, "nanosuit/ghost_titan", "Ghost Titan",
            "While cloaked, melee attacks deal bonus damage.",
            new Skill[]{GHOST_KILL}),
    VISOR_INSIGHT(Branch.STEALTH, "nanosuit/visor_insight", "Visor Insight",
            "In visor mode, mobs are highlighted by type (hostile, passive, etc.).",
            new Skill[]{GHOST_TITAN, GHOST_KILL}),

    // === SPEED BRANCH ===
    SPRINT_BOOST(Branch.SPEED, "nanosuit/sprint_boost", "Sprint Boost",
            "Increases sprint speed by 20%.",
            new Skill[]{}),

    DASH_JET(Branch.SPEED, "nanosuit/dash_jet", "Dash Jet",
            "Quickly dash in any direction with a double-tap.",
            new Skill[]{SPRINT_BOOST}),

    ENERGY_EFFICIENT_MOVEMENT(Branch.SPEED, "nanosuit/energy_efficient_movement", "Energy-Efficient Movement",
            "Reduces energy drain while sprinting or dashing by 25%.",
            new Skill[]{DASH_JET}),

    LOW_ENERGY_PROTOCOL(Branch.SPEED, "nanosuit/low_energy_protocol", "Low-Energy Protocol",
            "If below 40% energy, all movement drains 15% less power.",
            new Skill[]{ENERGY_EFFICIENT_MOVEMENT}),

    MOMENTUM_SYNC(Branch.SPEED, "nanosuit/momentum_sync", "Momentum Sync",
            "Dashing through enemies deals light damage and knockback.",
            new Skill[]{ENERGY_EFFICIENT_MOVEMENT}),

    KINETIC_LOOP(Branch.SPEED, "nanosuit/kinetic_loop", "Kinetic Loop",
            "Dash cooldown resets on kill (3-second cooldown).",
            new Skill[]{MOMENTUM_SYNC, LOW_ENERGY_PROTOCOL}),


    // === POWER BRANCH ===
    ARMOR_UP(Branch.POWER, "nanosuit/armor_up", "Armor Up",
            "Grants an extra 10% flat damage resistance.",
            new Skill[]{}),
    GROUND_SKIN(Branch.POWER, "nanosuit/ground_skim", "Ground Skim",
            "All fall damage is negated if you have enough energy.",
            new Skill[]{ARMOR_UP}),
    SHOCK_ABSORPTION(Branch.POWER, "nanosuit/shock_absorption", "Shock Absorption",
            "Negates fall damage from drops under 10 blocks.",
            new Skill[]{GROUND_SKIN}),
    SHOCKWAVE_SLAM(Branch.POWER, "nanosuit/shockwave_slam", "Shockwave Slam",
            "Falling from great height triggers a damaging AoE slam.",
            new Skill[]{SHOCK_ABSORPTION}),
    KINETIC_PUNCH(Branch.POWER, "nanosuit/kinetic_punch", "Kinetic Punch",
            "Melee attack knocks back and instantly kills weak enemies.",
            new Skill[]{ARMOR_UP}),
    POWER_SURGE(Branch.POWER, "nanosuit/power_surge", "Power Surge",
            "After a melee kill, next melee within 5s deals +50% damage.",
            new Skill[]{KINETIC_PUNCH}),

    NANO_REGEN(Branch.POWER, "nanosuit/nano_regen", "Nano-Regeneration",
            "Regenerate health gradually while in Armor mode, as long as your energy is above 50% and you haven't taken damage in the last 3 seconds.",
            new Skill[]{SHOCKWAVE_SLAM, POWER_SURGE}),
    FORTIFIED_CORE(Branch.POWER, "nanosuit/fortified_core", "Fortified Core",
            "When below 30% health, incoming damage uses 20% less energy to absorb.",
            new Skill[]{NANO_REGEN});

    public enum Branch {
        STEALTH, SPEED, POWER
    }

    private final Branch branch;
    private final String iconPath;
    private final String title;
    private final String description;
    private final Skill[] parents;

    Skill(Branch branch, String iconPath, String title, String description, Skill[] parents) {
        this.branch = branch;
        this.iconPath = iconPath;
        this.title = title;
        this.description = description;
        this.parents = parents == null ? new Skill[0] : parents;
    }

    public Branch getBranch() {
        return branch;
    }

    public String getIconPath() {
        return iconPath;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Skill[] getParents() {
        return Arrays.copyOf(parents, parents.length);
    }
}
