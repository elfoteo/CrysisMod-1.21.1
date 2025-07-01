package com.elfoteo.crysis.gui;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.gui.util.EntityDisposition;
import com.elfoteo.crysis.keybindings.ModKeyBindings;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import com.elfoteo.crysis.util.SuitModes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class NanosuitVisorInsights {
    private static final ResourceLocation VISOR_OVERLAY =
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/visor_overlay.png");
    private static final Rect HEALTHBAR = new Rect(6, 56, 98, 4);
    private static final Rect DECORATION = new Rect(0, 0, 110, 70);

    public static final HashMap<UUID, Entity> MARKED_ENTITIES = new HashMap<>();

    private static Entity lastEntity     = null;
    private static int    lastEntityTick = -1;

    public static Entity getLookedAtEntity() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null
                || Nanosuit.currentClientMode != SuitModes.VISOR.get()
                || !SkillData.isUnlocked(Skill.VISOR_INSIGHT, player)) {
            return null;
        }

        int currentTick = (int) mc.level.getGameTime();
        if (currentTick != lastEntityTick) {
            int rdChunks = mc.options.getEffectiveRenderDistance();
            double rayRange = rdChunks * 16 * 0.95 - 1;
            lastEntity = raycastEntityBypassBlocks(player, rayRange);
            lastEntityTick = currentTick;
        }
        return lastEntity;
    }

    private static Entity raycastEntityBypassBlocks(Player player, double range) {
        Vec3 eye  = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end  = eye.add(look.scale(range));
        AABB scanBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);

        List<Entity> candidates = player.level().getEntities(player, scanBox, e -> e != player && e.isPickable());
        return candidates.stream()
                .map(e -> {
                    AABB box = e.getBoundingBox().inflate(0.3D);
                    Optional<Vec3> hit = box.clip(eye, end);
                    return hit.map(v -> new EntityHit(e, eye.distanceTo(v))).orElse(null);
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(eh -> eh.distance))
                .map(eh -> eh.entity)
                .orElse(null);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || ModKeyBindings.MARK_ENTITY_KEY == null) {
            return;
        }

        if (!ModKeyBindings.MARK_ENTITY_KEY.consumeClick()) {
            return;
        }

        // Always attempt to unmark first
        int rdChunks = mc.options.getEffectiveRenderDistance();
        double fullRange = rdChunks * 16 * 2;
        Entity target = raycastEntityBypassBlocks(player, fullRange);
        if (target == null) {
            return;
        }

        UUID id = target.getUUID();
        if (MARKED_ENTITIES.containsKey(id)) {
            MARKED_ENTITIES.remove(id);
            return;
        }

        // Otherwise, only allow marking if in visor mode with skill
        if (Nanosuit.currentClientMode == SuitModes.VISOR.get()
                && SkillData.isUnlocked(Skill.VISOR_INSIGHT, player)) {
            MARKED_ENTITIES.put(id, target);
        }
    }

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.SCOREBOARD_SIDEBAR.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null
                || Nanosuit.currentClientMode != SuitModes.VISOR.get()
                || !SkillData.isUnlocked(Skill.VISOR_INSIGHT, player)) {
            return;
        }

        Entity target = getLookedAtEntity();
        if (target == null) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        int sw = gui.guiWidth();
        int sh = gui.guiHeight();
        int margin = 10;
        int baseX = sw - DECORATION.width - margin;
        int baseY = sh / 2 - DECORATION.height - margin;
        int textX = baseX + 6;
        int textY = baseY + 8;
        int lineHeight = 10;

        String name = target.getName().getString();
        String type = target.getType().toShortString();
        String disposition = EntityDisposition.getName(target);
        int dispositionColor = EntityDisposition.getColor(target);
        double distance = player.distanceTo(target);

        float hp    = target instanceof LivingEntity le ? le.getHealth() : -1f;
        float maxHp = target instanceof LivingEntity le ? le.getMaxHealth() : -1f;
        float armor = target instanceof LivingEntity le ? le.getArmorValue() : -1f;

        drawTexturedRect(gui, VISOR_OVERLAY, baseX, baseY, DECORATION.width, DECORATION.height,
                1f, 1f, 1f, 1f, 1f, 1f);

        gui.pose().pushPose();
        gui.pose().translate(textX, textY, 0);
        gui.pose().scale(0.75f, 0.75f, 1f);
        drawLabelValue(gui, "Name", name, 0, 0, 0x00FFFF);
        drawLabelValue(gui, "Type", type, 0, lineHeight, 0x00FFFF);
        drawLabelValue(gui, "Disposition", disposition, 0, lineHeight * 2, dispositionColor);
        drawLabelValue(gui, "Armor", armor >= 0 ? String.format("%.1f", armor) : "N/A", 0, lineHeight * 3 + 3, 0x00FFFF);
        drawLabelValue(gui, "Distance", String.format("%.1f m", distance), 0, lineHeight * 4 + 3, 0x00FFFF);
        drawLabelValue(gui, "HP", hp >= 0 ? String.format("%.1f / %.1f", hp, maxHp) : "N/A", 0, lineHeight * 5 + 3, 0x00FFFF);
        gui.pose().popPose();

        if (hp >= 0 && maxHp > 0) {
            drawHealthBar(gui, baseX, baseY, Math.max(0f, Math.min(1f, hp / maxHp)));
        }
    }

    private static void drawLabelValue(GuiGraphics gui, String label, String value, int x, int y, int color) {
        Minecraft mc = Minecraft.getInstance();
        gui.drawString(mc.font, Component.literal(label + ": " + value), x, y, color, false);
    }

    private static void drawHealthBar(GuiGraphics gui, int baseX, int baseY, float fraction) {
        int x = baseX + HEALTHBAR.x;
        int y = baseY + HEALTHBAR.y;
        int w = HEALTHBAR.width;
        int h = HEALTHBAR.height;
        int fill = (int) (w * fraction);
        gui.fill(x, y, x + w, y + h, 0xFF202020);
        if (fill > 0) {
            gui.fill(x, y, x + fill, y + h, 0xFF00FFFF);
        }
    }

    private static void drawTexturedRect(GuiGraphics gui, ResourceLocation tex,
                                         int x, int y, int w, int h,
                                         float uS, float vS,
                                         float r, float g, float b, float a) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(r, g, b, a);
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
