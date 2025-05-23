package com.elfoteo.tutorialmod.gui;

import com.elfoteo.tutorialmod.TutorialMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class NanosuitVisorInsights {
    private static final ResourceLocation VISOR_OVERLAY = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/visor_overlay.png");
    private static final Rect HEALTHBAR = new Rect(6, 50, 60, 4);
    private static final Rect DECORATION = new Rect(0, 0, 110, 70);

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        Entity lookedAt = raycastEntity(player, 32);
        if (!(lookedAt instanceof LivingEntity target)) return;

        GuiGraphics gui = event.getGuiGraphics();
        int sw = gui.guiWidth();
        int sh = gui.guiHeight();

        // === Entity Data ===
        String name = target.getName().getString();
        String type = target.getType().toShortString();
        float hp = target.getHealth(), maxHp = target.getMaxHealth();
        float armor = target.getArmorValue();
        String disposition = getDisposition(target);

        // === UI Coordinates ===
        int baseX = sw - 50 - DECORATION.width;
        int baseY = 150;
        int textX = baseX + 6;
        int textY = baseY + 8;
        int lineHeight = 10;

        // === Background Decoration ===
        drawTexturedRect(gui, VISOR_OVERLAY, baseX, baseY, DECORATION.width, DECORATION.height,
                1f, 1f, 1f, 1f, 1f);

        // === Text Scaling ===
        gui.pose().pushPose();
        gui.pose().translate(textX, textY, 0);
        gui.pose().scale(0.75f, 0.75f, 1f);

        // Text is drawn with coordinates scaled down
        drawLabelValue(gui, "Name", name, 0, 0, 0x00FFFF);
        drawLabelValue(gui, "Type", type, 0, lineHeight, 0x00FFFF);
        drawLabelValue(gui, "Disposition", disposition, 0, lineHeight * 2, getDispositionColor(disposition));
        drawLabelValue(gui, "Armor", String.format("%.1f", armor), 0, lineHeight * 3 + 3, 0x00FFFF);
        gui.pose().popPose();

        // === Health Bar ===
        drawHealthBar(gui, baseX, baseY, Math.max(0f, Math.min(1f, hp / maxHp)));

        // === Health Label (not scaled) ===
        gui.drawString(mc.font, Component.literal(String.format("HP: %.1f / %.1f", hp, maxHp)),
                textX, baseY + HEALTHBAR.y + HEALTHBAR.height + 4, 0x00FFFF, false);
    }

    private static void drawLabelValue(GuiGraphics gui, String label, String value, int x, int y, int color) {
        Minecraft mc = Minecraft.getInstance();
        String text = label + ": " + value;
        gui.drawString(mc.font, Component.literal(text), x, y, color, false);
    }

    private static String getDisposition(LivingEntity entity) {
        if (entity instanceof Enemy) return "Hostile"; // ✅ Fix: Zombies etc. now correctly show as hostile
        if (entity.getType().getCategory().isFriendly()) return "Passive";
        return "Neutral";
    }

    private static int getDispositionColor(String disposition) {
        return switch (disposition) {
            case "Hostile" -> 0xFF5555;
            case "Neutral" -> 0xFFFFAA;
            case "Passive" -> 0xAAFFAA;
            default -> 0xFFFFFF;
        };
    }

    private static void drawHealthBar(GuiGraphics gui, int baseX, int baseY, float fraction) {
        int x = baseX + HEALTHBAR.x, y = baseY + HEALTHBAR.y;
        int w = HEALTHBAR.width, h = HEALTHBAR.height;
        int fill = (int) (w * fraction);
        gui.fill(x, y, x + w, y + h, 0xFF202020);
        if (fill > 0) {
            gui.fill(x, y, x + fill, y + h, 0xFF00FFFF); // ✅ Cyan health bar fill
        }
    }

    private static Entity raycastEntity(Player player, double range) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 reachVec = eye.add(look.scale(range));

        AABB aabb = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        List<Entity> candidates = player.level().getEntities(player, aabb, e -> e != player && e.isPickable());

        return candidates.stream()
                .map(e -> {
                    AABB box = e.getBoundingBox().inflate(0.3);
                    Optional<Vec3> hit = box.clip(eye, reachVec);
                    return hit.map(vec -> new EntityHit(e, eye.distanceTo(vec))).orElse(null);
                })
                .filter(eh -> eh != null)
                .min(Comparator.comparingDouble(eh -> eh.distance))
                .map(eh -> eh.entity)
                .orElse(null);
    }

    private static void drawTexturedRect(GuiGraphics gui, ResourceLocation tex,
                                         int x, int y, int w, int h, float uS, float vS,
                                         float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(r, g, b, 1f);
        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(mat, x,     y,     0).setUv(0, 0);
        bb.addVertex(mat, x,     y + h, 0).setUv(0, vS);
        bb.addVertex(mat, x + w, y + h, 0).setUv(uS, vS);
        bb.addVertex(mat, x + w, y,     0).setUv(uS, 0);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private record EntityHit(Entity entity, double distance) {}

    private static final class Rect {
        final int x, y, width, height;
        Rect(int x, int y, int w, int h) {
            this.x = x; this.y = y; width = w; height = h;
        }
    }
}
