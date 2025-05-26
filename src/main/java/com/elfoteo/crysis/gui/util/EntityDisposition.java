package com.elfoteo.crysis.gui.util;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

public class EntityDisposition {

    public enum Disposition {
        HOSTILE(0xFF5555),
        NEUTRAL(0xFFFFAA),
        TAME(0x55FFFF),
        PASSIVE(0xAAFFAA),
        FRIENDLY_NPC(0x55FF55),
        BOSS(0xAA00FF),
        PLAYER(0x00AAFF),
        PROJECTILE(0x999999),
        UNKNOWN(0xFFFFFF);

        public final int color;

        Disposition(int color) {
            this.color = color;
        }
    }

    public static Disposition get(Entity entity) {
        if (entity instanceof Player) return Disposition.PLAYER;
        if (entity instanceof WitherBoss) return Disposition.BOSS;
        if (entity instanceof Enemy) return Disposition.HOSTILE;
        if (entity instanceof TamableAnimal tamable) {
            return tamable.isTame() ? Disposition.TAME : Disposition.PASSIVE;
        }
        if (entity instanceof AbstractVillager || entity instanceof WanderingTrader) {
            return Disposition.FRIENDLY_NPC;
        }
        if (entity.getType().getCategory().isFriendly()) return Disposition.PASSIVE;
        if (entity instanceof NeutralMob) return Disposition.NEUTRAL;
        if (entity instanceof Projectile) return Disposition.PROJECTILE;

        return Disposition.UNKNOWN;
    }

    public static int getColor(Entity entity) {
        return get(entity).color;
    }

    public static String getName(Entity entity) {
        return get(entity).name();
    }
}
