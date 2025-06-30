// NanosuitSkillTree.java
package com.elfoteo.crysis.gui;

import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.crysis.network.custom.skills.SkillPointsPacket;
import com.elfoteo.crysis.network.custom.skills.UnlockSkillPacket;
import com.elfoteo.crysis.skill.Skill;
import com.elfoteo.crysis.skill.SkillData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class NanosuitSkillTree extends Screen {
    private static final int NODE_SIZE = 32;
    private static final int TITLE_HEIGHT = 25;
    private static final int FOOTER_HEIGHT = 40;

    private static final int COLOR_NODE_BG_UNLOCKED   = 0xFF0A6E4D;
    private static final int COLOR_NODE_BG_LOCKED     = 0xFF333333;
    private static final int COLOR_NODE_BG_AVAILABLE  = 0xFF555555;
    private static final int COLOR_NODE_BORDER_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_NODE_BORDER_NORMAL    = 0xFF858585;
    private static final int COLOR_TITLE_BG      = 0xFF222222;
    private static final int COLOR_FOOTER_BG     = 0xFF222222;
    private static final int COLOR_LINE_LOCKED   = 0xFF555555;
    private static final int COLOR_LINE_UNLOCKED = 0xFF00FFFF;
    private static final int COLOR_LINE_BORDER   = 0xFF000000;

    private double scrollX, scrollY;
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;
    private boolean initialized = false;

    private final Map<Skill, SkillNode> nodeMap = new HashMap<>();
    private final List<Connection> connections = new ArrayList<>();

    public NanosuitSkillTree() {
        super(Component.translatable("gui." + CrysisMod.MOD_ID + ".skilltree"));
    }

    @Override
    protected void init() {
        super.init();

        PacketDistributor.sendToServer(new SkillPointsPacket(0, 0));
        PacketDistributor.sendToServer(new GetAllSkillsPacket(new HashMap<>()));

        if (!initialized) {
            // 1. Create a node for each Skill with placeholder (0,0)
            for (Skill s : Skill.values()) {
                nodeMap.put(s, new SkillNode(s, 0, 0));
            }

            // 2. Build parent→children adjacency
            Map<Skill, List<Skill>> childMap = new HashMap<>();
            for (Skill child : Skill.values()) {
                for (Skill parent : child.getParents()) {
                    childMap.computeIfAbsent(parent, k -> new ArrayList<>())
                            .add(child);
                }
            }

            // 3. Layout each root (no parents) at (0,0), then recurse
            for (Skill s : Skill.values()) {
                if (s.getParents().length == 0) {
                    layoutSubtree(s, 0, 0, childMap);
                }
            }

            // 4. Build connections
            for (Skill child : Skill.values()) {
                for (Skill parent : child.getParents()) {
                    connections.add(new Connection(nodeMap.get(parent), nodeMap.get(child)));
                }
            }

            computeInitialScroll();
            initialized = true;
        }

        addRenderableWidget(
                Button.builder(Component.translatable("gui.crysis.reset"), btn -> onReset())
                        .pos(width - 110, height - 30)
                        .size(100, 20)
                        .build());

        addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), btn -> onClose())
                        .pos(10, height - 30)
                        .size(60, 20)
                        .build());

        addRenderableWidget(
                Button.builder(Component.literal("X"), btn -> onClose())
                        .pos(width - 20, 5)
                        .size(20, 20)
                        .build());
    }

    private void layoutSubtree(Skill skill, int baseX, int baseY, Map<Skill, List<Skill>> childMap) {
        SkillNode node = nodeMap.get(skill);
        node.x = baseX;
        node.y = baseY;
        List<Skill> children = childMap.getOrDefault(skill, Collections.emptyList());
        for (Skill c : children) {
            // position child relative to this node
            layoutSubtree(c,
                    baseX + c.getX(),
                    baseY + c.getY(),
                    childMap);
        }
    }

    private void computeInitialScroll() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (SkillNode node : nodeMap.values()) {
            minX = Math.min(minX, node.x);
            maxX = Math.max(maxX, node.x + NODE_SIZE);
            minY = Math.min(minY, node.y);
            maxY = Math.max(maxY, node.y + NODE_SIZE);
        }

        scrollX = (width - (maxX - minX)) / 2 - minX;
        scrollY = (height - TITLE_HEIGHT - FOOTER_HEIGHT - (maxY - minY)) / 2 - minY + TITLE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float pt) {
        super.render(gui, mouseX, mouseY, pt);
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        gui.fill(0, 0, width, TITLE_HEIGHT, COLOR_TITLE_BG);
        gui.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);

        int availablePoints = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);
        gui.fill(0, height - FOOTER_HEIGHT, width, height, COLOR_FOOTER_BG);
        String ptsText = "Available Points: " + availablePoints;
        gui.drawString(font, ptsText, width / 2 - font.width(ptsText) / 2, height - 28, 0xFFFFFF);

        SkillNode hoveredNode = null;
        for (SkillNode n : nodeMap.values()) {
            if (n.isMouseOver(mouseX - scrollX, mouseY - scrollY)) {
                hoveredNode = n;
                break;
            }
        }

        int guiLeft = 0, guiTop = TITLE_HEIGHT;
        int guiWidth = width, guiHeight = height - TITLE_HEIGHT - FOOTER_HEIGHT;
        Window window = Minecraft.getInstance().getWindow();
        double sf = window.getGuiScale();
        int scissorX = (int)(guiLeft * sf);
        int scissorY = (int)(window.getHeight() - (guiTop + guiHeight) * sf);
        int scissorW = (int)(guiWidth * sf);
        int scissorH = (int)(guiHeight * sf);
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

        gui.pose().pushPose();
        gui.pose().translate(scrollX, scrollY, 0);

        for (Connection c : connections) {
            drawConnection(gui, c);
        }
        for (SkillNode n : nodeMap.values()) {
            n.render(gui, (int)(mouseX - scrollX), (int)(mouseY - scrollY));
        }
        gui.pose().popPose();
        RenderSystem.disableScissor();

        if (hoveredNode != null) {
            var tip = new ArrayList<Component>();
            tip.add(Component.literal(hoveredNode.skill.getTitle()).withStyle(s -> s.withBold(true)));
            tip.add(Component.literal(hoveredNode.skill.getDescription()));

            boolean unlocked = SkillData.isUnlocked(hoveredNode.skill, player);
            boolean available = SkillData.isAvailable(hoveredNode.skill, player);

            if (unlocked) {
                tip.add(Component.literal("Status: Unlocked").withStyle(s -> s.withColor(0x00FF00)));
            } else if (available && availablePoints > 0) {
                tip.add(Component.literal("Status: Available").withStyle(s -> s.withColor(0xFFFF00)));
                tip.add(Component.literal("Cost: 1 skill point"));
            } else if (available) {
                tip.add(Component.literal("Status: Available").withStyle(s -> s.withColor(0xFFFF00)));
                tip.add(Component.literal("No skill points left").withStyle(s -> s.withColor(0xFF0000)));
            } else {
                tip.add(Component.literal("Status: Locked").withStyle(s -> s.withColor(0xFF0000)));
                tip.add(Component.literal("Requires a parent skill unlocked"));
            }

            gui.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void drawConnection(GuiGraphics gui, Connection c) {
        int x1 = c.parent.x + NODE_SIZE/2;
        int y1 = c.parent.y + NODE_SIZE/2;
        int x2 = c.child.x  + NODE_SIZE/2;
        int y2 = c.child.y  + NODE_SIZE/2;

        boolean parentUnlocked = SkillData.isUnlocked(c.parent.skill, Minecraft.getInstance().player);
        int lineColor = parentUnlocked ? COLOR_LINE_UNLOCKED : COLOR_LINE_LOCKED;

        gui.hLine(Math.min(x1, x2), Math.max(x1, x2), y1-1, COLOR_LINE_BORDER);
        gui.hLine(Math.min(x1, x2), Math.max(x1, x2), y1,   lineColor);
        gui.hLine(Math.min(x1, x2), Math.max(x1, x2), y1+1, COLOR_LINE_BORDER);

        gui.vLine(x2-1, Math.min(y1, y2), Math.max(y1, y2), COLOR_LINE_BORDER);
        gui.vLine(x2,   Math.min(y1, y2), Math.max(y1, y2), lineColor);
        gui.vLine(x2+1, Math.min(y1, y2), Math.max(y1, y2), COLOR_LINE_BORDER);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (my < TITLE_HEIGHT || my > height - FOOTER_HEIGHT) {
            return super.mouseClicked(mx, my, btn);
        }
        if (btn == 1) {
            lastMouseX = mx; lastMouseY = my; dragging = true; return true;
        }
        if (btn == 0) {
            double ax = mx - scrollX, ay = my - scrollY;
            Player player = Minecraft.getInstance().player;
            if (player == null) return super.mouseClicked(mx, my, btn);
            int pts = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);
            for (SkillNode n : nodeMap.values()) {
                if (n.isMouseOver(ax, ay)) {
                    if (!SkillData.isUnlocked(n.skill, player)
                            && SkillData.isAvailable(n.skill, player)
                            && pts > 0) {
                        PacketDistributor.sendToServer(
                                new UnlockSkillPacket(n.skill, UnlockSkillPacket.Success.SUCCESS));
                        player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, pts-1);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 1 && dragging) {
            scrollX += (mx - lastMouseX);
            scrollY += (my - lastMouseY);
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 1 && dragging) {
            dragging = false; return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    private void onReset() {
        PacketDistributor.sendToServer(new ResetSkillsPacket());
        onClose();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private static class Connection {
        final SkillNode parent, child;
        Connection(SkillNode p, SkillNode c) {
            this.parent = p; this.child = c;
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static class SkillNode {
        // now mutable
        int x, y;
        final Skill skill;
        final ResourceLocation icon;

        SkillNode(Skill s, int x, int y) {
            this.skill = s;
            this.x = x;
            this.y = y;
            this.icon = ResourceLocation.fromNamespaceAndPath(
                    "crysis",
                    "textures/gui/" + s.getIconPath() + ".png"
            );
        }

        void render(GuiGraphics gui, int mx, int my) {
            boolean hover = isMouseOver(mx, my);
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            boolean unlocked  = SkillData.isUnlocked(skill, player);
            boolean available = SkillData.isAvailable(skill, player);
            int pts = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);

            int bg = unlocked
                    ? COLOR_NODE_BG_UNLOCKED
                    : (available && pts>0
                    ? COLOR_NODE_BG_AVAILABLE
                    : COLOR_NODE_BG_LOCKED);

            int border = hover
                    ? COLOR_NODE_BORDER_HIGHLIGHT
                    : COLOR_NODE_BORDER_NORMAL;

            gui.fill(x, y, x+NODE_SIZE, y+NODE_SIZE, bg);
            gui.hLine(x, x+NODE_SIZE-1, y,            border);
            gui.hLine(x, x+NODE_SIZE-1, y+NODE_SIZE-1, border);
            gui.vLine(x, y, y+NODE_SIZE-1,            border);
            gui.vLine(x+NODE_SIZE-1, y, y+NODE_SIZE-1, border);

            RenderSystem.enableBlend();
            int pad = 4;
            gui.blit(icon,
                    x+pad, y+pad,
                    0, 0,
                    NODE_SIZE-pad*2, NODE_SIZE-pad*2,
                    NODE_SIZE-pad*2, NODE_SIZE-pad*2);
            RenderSystem.disableBlend();

            if (!unlocked && !available) {
                gui.fill(x, y, x+NODE_SIZE, y+NODE_SIZE, 0x99000000);
            }
        }

        boolean isMouseOver(double mx, double my) {
            return mx>=x && mx<x+NODE_SIZE && my>=y && my<y+NODE_SIZE;
        }
    }
}
