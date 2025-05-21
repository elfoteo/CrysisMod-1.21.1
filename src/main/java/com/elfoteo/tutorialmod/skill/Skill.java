// Skill.java
package com.elfoteo.tutorialmod.skill;

import java.util.Arrays;

/**
 * An enum representing every skill in the Nanosuit tree.
 * Each entry knows its x/y position, icon path (relative to textures/gui), title,
 * description (tooltip), and which Skills are its direct parents.
 */
public enum Skill {
    // === STEALTH BRANCH ===
    SILENT_STEP(
        0, -200,
        "nanosuit/silent_step",
        "Silent Step",
        "Movement makes no sound or vibrations while walking or running.",
        new Skill[]{} // no parents
    ),
    THERMAL_DAMPENERS(
        120, -260,
        "nanosuit/thermal_dampeners",
        "Thermal Dampeners",
        "Emits less heat while cloaked, reducing visibility to thermal vision modes.",
        new Skill[]{SILENT_STEP}
    ),
    GROUND_SKIM(
        120, -140,
        "nanosuit/ground_skim",
        "Ground Skim",
        "All fall damage is negated if you have enough energy.",
        new Skill[]{SILENT_STEP}
    ),
    DYNAMIC_CLOAKING(
        240, -260,
        "nanosuit/dynamic_cloaking",
        "Dynamic Cloaking",
        "Cloak drains 50% less energy while standing still.",
        new Skill[]{THERMAL_DAMPENERS}
    ),
    GHOST_KILL(
        240, -140,
        "nanosuit/ghost_kill",
        "Ghost Kill",
        "Melee kills in stealth make no noise and do not break cloak, reduces also the cloak activation cooldown by .5s",
        new Skill[]{GROUND_SKIM}
    ),
    CLOAK_RECALL(
        360, -200,
        "nanosuit/cloak_recall",
        "Cloak Recall",
        "Automatically reactivates cloak 2s after taking light damage, if energy allows.",
        new Skill[]{DYNAMIC_CLOAKING, GHOST_KILL}
    ),
    GHOST_TITAN(
        480, -260,
        "nanosuit/ghost_titan",
        "Ghost Titan",
        "While cloaked, melee attacks deal bonus damage.",
        new Skill[]{CLOAK_RECALL}
    ),
    SHADOW_VEIL(
        480, -140,
        "nanosuit/shadow_veil",
        "Shadow Veil",
        "After a stealth kill, cloak duration is slightly extended.",
        new Skill[]{CLOAK_RECALL}
    ),
    VISOR_INSIGHT(
        600, -200,
        "nanosuit/visor_insight",
        "Visor Insight",
        "In visor mode, mobs are highlighted by type (hostile, passive, etc.).",
        new Skill[]{GHOST_TITAN, SHADOW_VEIL}
    ),

    // === SPEED BRANCH ===
    SPRINT_BOOST(
        0, 0,
        "nanosuit/sprint_boost",
        "Sprint Boost",
        "Increases sprint speed by 20%.",
        new Skill[]{}
    ),
    DASH_JET(
        120, -40,
        "nanosuit/dash_jet",
        "Dash Jet",
        "Quickly dash in any direction with a double-tap.",
        new Skill[]{SPRINT_BOOST}
    ),
    ENERGY_EFFICIENT_MOVEMENT(
        120, 40,
        "nanosuit/energy_efficient_movement",
        "Energy-Efficient Movement",
        "Reduces energy drain while sprinting or dashing by 25%.",
        new Skill[]{SPRINT_BOOST}
    ),
    LOW_ENERGY_PROTOCOL(
        240, -40,
        "nanosuit/low_energy_protocol",
        "Low-Energy Protocol",
        "If below 40% energy, all movement drains 15% less power.",
        new Skill[]{DASH_JET}
    ),
    AERIAL_CONTROL(
        240, 40,
        "nanosuit/aerial_control",
        "Aerial Control",
        "Enhances jump height and air control.",
        new Skill[]{ENERGY_EFFICIENT_MOVEMENT}
    ),
    MOMENTUM_SYNC(
        360, 0,
        "nanosuit/momentum_sync",
        "Momentum Sync",
        "Dashing through enemies deals light damage and knockback.",
        new Skill[]{LOW_ENERGY_PROTOCOL, AERIAL_CONTROL}
    ),
    KINETIC_LOOP(
        480, 0,
        "nanosuit/kinetic_loop",
        "Kinetic Loop",
        "Dash cooldown resets on kill (3-second cooldown).",
        new Skill[]{MOMENTUM_SYNC}
    ),

    // === POWER BRANCH ===
    ARMOR_UP(
        0, 200,
        "nanosuit/armor_up",
        "Armor Up",
        "Grants an extra 10% flat damage resistance.",
        new Skill[]{}
    ),
    SHOCK_ABSORPTION(
        120, 160,
        "nanosuit/shock_absorption",
        "Shock Absorption",
        "Negates fall damage from drops under 10 blocks.",
        new Skill[]{ARMOR_UP}
    ),
    KINETIC_PUNCH(
        120, 240,
        "nanosuit/kinetic_punch",
        "Kinetic Punch",
        "Melee attack knocks back and instantly kills weak enemies.",
        new Skill[]{ARMOR_UP}
    ),
    SHOCKWAVE_SLAM(
        240, 160,
        "nanosuit/shockwave_slam",
        "Shockwave Slam",
        "Falling from great height triggers a damaging AoE slam.",
        new Skill[]{SHOCK_ABSORPTION}
    ),
    POWER_SURGE(
        240, 240,
        "nanosuit/power_surge",
        "Power Surge",
        "After a melee kill, next melee within 5s deals +50% damage.",
        new Skill[]{KINETIC_PUNCH}
    ),
    NANO_REGEN(
        360, 200,
        "nanosuit/nano_regen",
        "Nano-Regeneration",
        "While in armor mode and energy > 50%, gradually regenerates health.",
        new Skill[]{SHOCKWAVE_SLAM, POWER_SURGE}
    ),
    FORTIFIED_CORE(
        480, 200,
        "nanosuit/fortified_core",
        "Fortified Core",
        "When below 30% health, incoming damage uses 20% less energy to absorb.",
        new Skill[]{NANO_REGEN}
    );

    private final int x, y;
    private final String iconPath;
    private final String title;
    private final String description;
    private final Skill[] parents;

    Skill(int x, int y, String iconPath, String title, String description, Skill[] parents) {
        this.x = x;
        this.y = y;
        this.iconPath = iconPath;
        this.title = title;
        this.description = description;
        this.parents = parents == null ? new Skill[0] : parents;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /**
     * Returns the icon path relative to
     *   "textures/gui/<iconPath>.png"
     */
    public String getIconPath() {
        return iconPath;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns an array of direct parent‐skills.
     * If empty, this is a root node.
     */
    public Skill[] getParents() {
        return Arrays.copyOf(parents, parents.length);
    }
}
