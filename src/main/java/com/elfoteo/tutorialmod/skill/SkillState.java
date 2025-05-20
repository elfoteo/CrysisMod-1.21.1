package com.elfoteo.tutorialmod.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class SkillState {
    private final Skill skill;
    private boolean unlocked;

    public SkillState(Skill skill, boolean unlocked) {
        this.skill = skill;
        this.unlocked = unlocked;
    }

    public Skill getSkill() {
        return skill;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public static final Codec<Skill> SKILL_CODEC = Codec.STRING.xmap(Skill::valueOf, Skill::name);

    /**
     * A Codec that serializes this as a pair:  { "skill": "<Skill.name()>", "unlocked": <true|false> }
     */
    public static final Codec<SkillState> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            SKILL_CODEC.fieldOf("skill").forGetter(SkillState::getSkill),
            Codec.BOOL.fieldOf("unlocked").forGetter(SkillState::isUnlocked)
        ).apply(instance, SkillState::new)
    );
}
