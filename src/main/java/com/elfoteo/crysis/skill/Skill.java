// Skill.java
package com.elfoteo.crysis.skill;

import java.util.Arrays;

/**
 * An enum representing every skill in the Nanosuit tree.
 * Each entry now includes its branch, x/y icon path (relative to textures/gui),
 * title, description, which Skills are its direct parents, and a fixed (x,y) position.
 * All (x,y) here are now relative to the first parent’s coords; roots stay at (0,0).
 */
public enum Skill {
    // Root:
    NANOSUIT(
            0, 0,
            "nanosuit/nanosuit",
            "Nanosuit",
            "Start unlocking all the base powers of the nanosuit",
            new Skill[]{}),

    // === STEALTH BRANCH ===
    SILENT_STEP(
            100, -100,
            "nanosuit/silent_step",
            "Silent Step",
            "Movement makes no sound or vibrations while walking or running.",
            new Skill[]{NANOSUIT}),
    THERMAL_DAMPENERS(
            100, -20,
            "nanosuit/thermal_dampeners",
            "Thermal Dampeners",
            "Emits less heat while cloaked, reducing visibility to thermal vision modes.",
            new Skill[]{SILENT_STEP}),
    DYNAMIC_CLOAKING(
            100, 0,
            "nanosuit/dynamic_cloaking",
            "Dynamic Cloaking",
            "Cloak drains 25% less energy overall.",
            new Skill[]{THERMAL_DAMPENERS}),
    PREDATORY_STRIKE(
            100, 20,
            "nanosuit/predatory_strike",
            "Predatory Strike",
            "Killing a target while cloaked regenerates 10 energy.",
            new Skill[]{SILENT_STEP}),
    GHOST_KILL(
            100, 0,
            "nanosuit/ghost_kill",
            "Ghost Kill",
            "Kills in stealth no longer break cloak, cloak activation cooldown in case the entity didn't die is reduced by 0.5s",
            new Skill[]{PREDATORY_STRIKE}),
    GHOST_TITAN(
            100, -20,
            "nanosuit/ghost_titan",
            "Ghost Titan",
            "While cloaked, melee attacks deal bonus damage.",
            new Skill[]{GHOST_KILL, DYNAMIC_CLOAKING}),
    VISOR_INSIGHT(
            100, 0,
            "nanosuit/visor_insight",
            "Visor Insight",
            "In visor mode, mobs are highlighted by type (hostile, passive, etc.).",
            new Skill[]{GHOST_TITAN}),

    // === MOVEMENT BRANCH ===
    SOFT_FALL(
            140, 0,
            "nanosuit/soft_fall",
            "Soft fall",
            "All fall damage is negated if you have enough energy.",
            new Skill[]{NANOSUIT}),
    POWER_JUMP(
            60, 0,
            "nanosuit/power_jump",
            "Power jump",
            "Crouch to charge a power jump. Release by jumping to launch into the air.",
            new Skill[]{SOFT_FALL}),
    SHOCK_ABSORPTION(
            60, 0,
            "nanosuit/shock_absorption",
            "Shock Absorption",
            "Negates fall damage from drops under 10 blocks.",
            new Skill[]{POWER_JUMP}),
    POWER_AMPLIFIER(
            100, -20,
            "nanosuit/power_amplifier",
            "Power Amplifier",
            "Increases the height and distance of a charged power jump.",
            new Skill[]{SHOCK_ABSORPTION}),
    QUICK_CHARGE(
            100, 20,
            "nanosuit/quick_charge",
            "Quick Charge",
            "Reduces the time needed to fully charge a power jump while crouching.",
            new Skill[]{SHOCK_ABSORPTION}),
    EFFICIENT_BURST(
            100, 0,
            "nanosuit/efficient_burst",
            "Efficient Burst",
            "Reduces the energy cost of power jumps by 30%.",
            new Skill[]{POWER_AMPLIFIER}),

    FAST_RECOVERY(
            100, 0,
            "nanosuit/fast_recovery",
            "Fast Recovery",
            "Reduces the energy regeneration delay after a power jump from 3 seconds to 2.",
            new Skill[]{QUICK_CHARGE}),
    SHOCKWAVE_SLAM(
            100, -20,
            "nanosuit/shockwave_slam",
            "Shockwave Slam",
            "Falling from great height triggers a damaging AoE slam.",
            new Skill[]{EFFICIENT_BURST, FAST_RECOVERY}),

    // === POWER BRANCH ===
    ARMOR_UP(
            100, 100,
            "nanosuit/armor_up",
            "Armor Up",
            "Grants an extra 10% flat damage resistance in armor mode.",
            new Skill[]{NANOSUIT}),
    SPRINT_BOOST(
            100, 20,
            "nanosuit/sprint_boost",
            "Sprint Boost",
            "Increases sprint speed by 20%.",
            new Skill[]{ARMOR_UP}),
    ENHANCED_BATTERIES(
            100, -20,
            "nanosuit/enhanced_batteries",
            "Enhanced Batteries",
            "Increases the suit total energy by 15%",
            new Skill[]{ARMOR_UP}),
    KINETIC_PUNCH(
            100, 0,
            "nanosuit/kinetic_punch",
            "Kinetic Punch",
            "Melee attack knocks back and instantly kills weak enemies.",
            new Skill[]{SPRINT_BOOST}),
    POWER_SURGE(
            100, 0,
            "nanosuit/power_surge",
            "Power Surge",
            "After a melee kill, next melee within 5s deals +50% damage.",
            new Skill[]{ENHANCED_BATTERIES}),
    NANO_REGEN(
            100, 20,
            "nanosuit/nano_regen",
            "Nano-Regeneration",
            "Regenerate health gradually while in Armor mode, as long as your energy is above 50% and you haven't taken damage in the last 3 seconds.",
            new Skill[]{POWER_SURGE, KINETIC_PUNCH}),
    FORTIFIED_CORE(
            100, 0,
            "nanosuit/fortified_core",
            "Fortified Core",
            "When below 30% health, incoming damage uses 20% less energy to absorb.",
            new Skill[]{NANO_REGEN});

    public enum Branch {
        NANOSUIT
    }

    private final int coordX;
    private final int coordY;
    private final String iconPath;
    private final String title;
    private final String description;
    private final Skill[] parents;

    Skill(
            int x,
            int y,
            String iconPath,
            String title,
            String description,
            Skill[] parents
    ) {
        this.coordX = x;
        this.coordY = y;
        this.iconPath = iconPath;
        this.title = title;
        this.description = description;
        this.parents = (parents == null) ? new Skill[0] : parents;
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
