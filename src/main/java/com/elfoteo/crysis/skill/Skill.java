// Skill.java
package com.elfoteo.crysis.skill;

import java.util.Arrays;

/**
 * An enum representing every skill in the Nanosuit tree.
 * Each entry now includes its branch, x/y icon path (relative to textures/gui),
 * title, description, which Skills are its direct parents, and a fixed (x,y) position.
 */
public enum Skill {
    // ───────────────────────────────────────────────────────────────────────────
    // Note: The (x, y) values below are “predetermined coordinates” that you
    // can adjust to taste.  For example, (0,0) might be the very left/top of the
    // skill‐tree, then x increases by 100 per depth and y by 80 per vertical step.
    // ───────────────────────────────────────────────────────────────────────────

    // Root:
    NANOSUIT(
            Branch.NANOSUIT,
            0, 0,
            "nanosuit/nanosuit",
            "Nanosuit",
            "Start unlocking all the base powers of the nanosuit",
            new Skill[]{}),

    // === STEALTH BRANCH ===
    SILENT_STEP(
            Branch.NANOSUIT,
            100, -80,
            "nanosuit/silent_step",
            "Silent Step",
            "Movement makes no sound or vibrations while walking or running.",
            new Skill[]{NANOSUIT}),
    THERMAL_DAMPENERS(
            Branch.NANOSUIT,
            200, -80,
            "nanosuit/thermal_dampeners",
            "Thermal Dampeners",
            "Emits less heat while cloaked, reducing visibility to thermal vision modes.",
            new Skill[]{SILENT_STEP}),
    DYNAMIC_CLOAKING(
            Branch.NANOSUIT,
            300, -80,
            "nanosuit/dynamic_cloaking",
            "Dynamic Cloaking",
            "Cloak drains 25% less energy overall.",
            new Skill[]{THERMAL_DAMPENERS}),
    PREDATORY_STRIKE(
            Branch.NANOSUIT,
            200, -40,
            "nanosuit/predatory_strike",
            "Predatory Strike",
            "Killing a target while cloaked regenerates 10 energy.",
            new Skill[]{SILENT_STEP}),
    GHOST_KILL(
            Branch.NANOSUIT,
            400, -80,
            "nanosuit/ghost_kill",
            "Ghost Kill",
            "Kills in stealth no longer break cloak, cloak activation cooldown in case the entity didn't die is reduced by 0.5s",
            new Skill[]{DYNAMIC_CLOAKING, PREDATORY_STRIKE}),
    GHOST_TITAN(
            Branch.NANOSUIT,
            500, -80,
            "nanosuit/ghost_titan",
            "Ghost Titan",
            "While cloaked, melee attacks deal bonus damage.",
            new Skill[]{GHOST_KILL}),
    VISOR_INSIGHT(
            Branch.NANOSUIT,
            600, -80,
            "nanosuit/visor_insight",
            "Visor Insight",
            "In visor mode, mobs are highlighted by type (hostile, passive, etc.).",
            new Skill[]{GHOST_TITAN, GHOST_KILL}),

    // === MOVEMENT BRANCH ===
    SOFT_FALL(
            Branch.NANOSUIT,
            100, 0,
            "nanosuit/soft_fall",
            "Soft fall",
            "All fall damage is negated if you have enough energy.",
            new Skill[]{NANOSUIT}),
    POWER_JUMP(
            Branch.NANOSUIT,
            200, 0,
            "nanosuit/power_jump",
            "Power jump",
            "Crouch to charge a power jump. Release by jumping to launch into the air.",
            new Skill[]{SOFT_FALL}),
    SHOCK_ABSORPTION(
            Branch.NANOSUIT,
            200, 40,
            "nanosuit/shock_absorption",
            "Shock Absorption",
            "Negates fall damage from drops under 10 blocks.",
            new Skill[]{SOFT_FALL}),
    SHOCKWAVE_SLAM(
            Branch.NANOSUIT,
            300, 0,
            "nanosuit/shockwave_slam",
            "Shockwave Slam",
            "Falling from great height triggers a damaging AoE slam.",
            new Skill[]{SHOCK_ABSORPTION}),

    QUICK_CHARGE(
            Branch.NANOSUIT,
            300, 40,
            "nanosuit/quick_charge",
            "Energy-Efficient Movement",
            "Reduces the time needed to fully charge a power jump while crouching.",
            new Skill[]{POWER_JUMP}),

    EFFICIENT_BURST(
            Branch.NANOSUIT,
            400, 0,
            "nanosuit/efficient_burst",
            "Efficient Burst",
            "Reduces the energy cost of power jumps by 30%.",
            new Skill[]{QUICK_CHARGE}),

    POWER_AMPLIFIER(
            Branch.NANOSUIT,
            500, 0,
            "nanosuit/power_amplifier",
            "Power Amplifier",
            "Increases the height and distance of a charged power jump.",
            new Skill[]{EFFICIENT_BURST, SHOCKWAVE_SLAM}),

    FAST_RECOVERY(
            Branch.NANOSUIT,
            600, 0,
            "nanosuit/fast_recovery",
            "Fast Recovery",
            "Reduces the energy regeneration delay after a power jump from 3 seconds to 2.",
            new Skill[]{POWER_AMPLIFIER}),

    // === POWER BRANCH ===
    ARMOR_UP(
            Branch.NANOSUIT,
            100, 80,
            "nanosuit/armor_up",
            "Armor Up",
            "Grants an extra 10% flat damage resistance.",
            new Skill[]{NANOSUIT}),
    SPRINT_BOOST(
            Branch.NANOSUIT,
            200, 80,
            "nanosuit/sprint_boost",
            "Sprint Boost",
            "Increases sprint speed by 20%.",
            new Skill[]{ARMOR_UP}),
    ENHANCED_BATTERIES(
            Branch.NANOSUIT,
            300, 80,
            "nanosuit/enhanced_batteries",
            "Enhanced Batteries",
            "Increases the suit total energy by 15%",
            new Skill[]{SPRINT_BOOST}),
    KINETIC_PUNCH(
            Branch.NANOSUIT,
            200, 120,
            "nanosuit/kinetic_punch",
            "Kinetic Punch",
            "Melee attack knocks back and instantly kills weak enemies.",
            new Skill[]{ARMOR_UP}),
    POWER_SURGE(
            Branch.NANOSUIT,
            300, 120,
            "nanosuit/power_surge",
            "Power Surge",
            "After a melee kill, next melee within 5s deals +50% damage.",
            new Skill[]{KINETIC_PUNCH}),

    NANO_REGEN(
            Branch.NANOSUIT,
            400, 80,
            "nanosuit/nano_regen",
            "Nano-Regeneration",
            "Regenerate health gradually while in Armor mode, as long as your energy is above 50% and you haven't taken damage in the last 3 seconds.",
            new Skill[]{SPRINT_BOOST, POWER_SURGE}),
    FORTIFIED_CORE(
            Branch.NANOSUIT,
            500, 80,
            "nanosuit/fortified_core",
            "Fortified Core",
            "When below 30% health, incoming damage uses 20% less energy to absorb.",
            new Skill[]{NANO_REGEN});

    public enum Branch {
        NANOSUIT
    }

    private final Branch branch;
    private final int coordX;
    private final int coordY;
    private final String iconPath;
    private final String title;
    private final String description;
    private final Skill[] parents;

    Skill(
            Branch branch,
            int x,
            int y,
            String iconPath,
            String title,
            String description,
            Skill[] parents
    ) {
        this.branch = branch;
        this.coordX = x;
        this.coordY = y;
        this.iconPath = iconPath;
        this.title = title;
        this.description = description;
        this.parents = (parents == null) ? new Skill[0] : parents;
    }

    public Branch getBranch() {
        return branch;
    }

    public int getX() {
        return coordX;
    }

    public int getY() {
        return coordY;
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
