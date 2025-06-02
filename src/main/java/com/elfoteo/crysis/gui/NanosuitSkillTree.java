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

/**
 * A skill-tree GUI that places each node at its predetermined (x,y).
 * All dynamic layout logic has been removed.  You simply read the fixed
 * coordinates from each Skill.
 */
@OnlyIn(Dist.CLIENT)
public class NanosuitSkillTree extends Screen {
    // ---- Constants ----
    private static final int NODE_SIZE = 32;
    private static final int TITLE_HEIGHT = 25;
    private static final int FOOTER_HEIGHT = 40;

    // Colors
    private static final int COLOR_BG = 0xFF1A1A1A;
    private static final int COLOR_NODE_BG_UNLOCKED = 0xFF0A6E4D;
    private static final int COLOR_NODE_BG_LOCKED = 0xFF333333;
    private static final int COLOR_NODE_BG_AVAILABLE = 0xFF555555;
    private static final int COLOR_NODE_BORDER_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_NODE_BORDER_NORMAL = 0xFF858585;
    private static final int COLOR_TITLE_BG = 0xFF222222;
    private static final int COLOR_FOOTER_BG = 0xFF222222;
    private static final int COLOR_LINE_LOCKED = 0xFF555555;
    private static final int COLOR_LINE_UNLOCKED = 0xFF00FFFF;
    private static final int COLOR_LINE_BORDER = 0xFF000000;

    // UI state
    private int scrollX, scrollY;
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;
    private boolean initialized = false;

    // One SkillNode per Skill, built at init()
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
            // Simply take each Skill’s predetermined (x,y) and create a node
            for (Skill s : Skill.values()) {
                nodeMap.put(s, new SkillNode(s, s.getX(), s.getY()));
            }
            for (Skill child : Skill.values()) {
                for (Skill parent : child.getParents()) {
                    connections.add(new Connection(nodeMap.get(parent), nodeMap.get(child)));
                }
            }
            computeInitialScroll();
            initialized = true;
        }

        // "Reset" button at bottom-right
        addRenderableWidget(
                Button.builder(Component.translatable("gui.crysis.reset"), btn -> onReset())
                        .pos(width - 110, height - 30)
                        .size(100, 20)
                        .build());

        // "Back" button at bottom-left
        addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), btn -> onClose())
                        .pos(10, height - 30)
                        .size(60, 20)
                        .build());

        // "X" close in top-right
        addRenderableWidget(
                Button.builder(Component.literal("X"), btn -> onClose())
                        .pos(width - 20, 5)
                        .size(20, 20)
                        .build());
    }

    /**
     * After all nodes exist, center the bounding box of (x,y) so the skill-tree
     * sits neatly in the middle of the GUI.
     */
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

        // 1) Background fill
        gui.fill(0, 0, width, height, COLOR_BG);

        // 2) Title bar
        gui.fill(0, 0, width, TITLE_HEIGHT, COLOR_TITLE_BG);
        gui.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);

        // 3) Footer with available points
        int availablePoints = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);
        gui.fill(0, height - FOOTER_HEIGHT, width, height, COLOR_FOOTER_BG);
        String ptsText = "Available Points: " + availablePoints;
        gui.drawString(font, ptsText, width / 2 - font.width(ptsText) / 2, height - 28, 0xFFFFFF);

        // 4) Find hovered node
        SkillNode hoveredNode = null;
        for (SkillNode n : nodeMap.values()) {
            if (n.isMouseOver(mouseX - scrollX, mouseY - scrollY)) {
                hoveredNode = n;
                break;
            }
        }

        // 5) Scissor region for the tree (exclude title/footer)
        int guiLeft = 0;
        int guiTop = TITLE_HEIGHT;
        int guiWidth = width;
        int guiHeight = height - TITLE_HEIGHT - FOOTER_HEIGHT;
        Window window = Minecraft.getInstance().getWindow();
        double scaleFactor = window.getGuiScale();
        int winW = window.getWidth();
        int winH = window.getHeight();
        int scissorX = (int) (guiLeft * scaleFactor);
        int scissorY = (int) (winH - (guiTop + guiHeight) * scaleFactor);
        int scissorW = (int) (guiWidth * scaleFactor);
        int scissorH = (int) (guiHeight * scaleFactor);
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

        // 6) Translate and draw connections then nodes
        gui.pose().pushPose();
        gui.pose().translate(scrollX, scrollY, 0);

        for (Connection c : connections) {
            drawConnection(gui, c);
        }
        for (SkillNode n : nodeMap.values()) {
            n.render(gui, mouseX - scrollX, mouseY - scrollY);
        }
        gui.pose().popPose();
        RenderSystem.disableScissor();

        // 7) Tooltip for hovered node
        if (hoveredNode != null) {
            var tip = new ArrayList<Component>();
            tip.add(Component.literal(hoveredNode.skill.getTitle()).withStyle(s -> s.withBold(true)));
            tip.add(Component.literal(hoveredNode.skill.getDescription()));

            boolean unlocked = SkillData.isUnlocked(hoveredNode.skill);
            boolean available = SkillData.isAvailable(hoveredNode.skill);

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
        int x1 = c.parent.x + NODE_SIZE / 2;
        int y1 = c.parent.y + NODE_SIZE / 2;
        int x2 = c.child.x + NODE_SIZE / 2;
        int y2 = c.child.y + NODE_SIZE / 2;

        // Color depends on whether the parent skill is unlocked
        boolean parentUnlocked = SkillData.isUnlocked(c.parent.skill);
        int lineColor = parentUnlocked ? COLOR_LINE_UNLOCKED : COLOR_LINE_LOCKED;

        // Draw an L-shaped connector with a bending point
        int midX = x2;
        int midY = y1;

        // Horizontal segment (parent → bend)
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1 - 1, COLOR_LINE_BORDER);
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1, lineColor);
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1 + 1, COLOR_LINE_BORDER);

        // Vertical segment (bend → child)
        gui.vLine(midX - 1, Math.min(midY, y2), Math.max(midY, y2), COLOR_LINE_BORDER);
        gui.vLine(midX, Math.min(midY, y2), Math.max(midY, y2), lineColor);
        gui.vLine(midX + 1, Math.min(midY, y2), Math.max(midY, y2), COLOR_LINE_BORDER);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Ignore clicks in the title or footer area
        if (my < TITLE_HEIGHT || my > height - FOOTER_HEIGHT) {
            return super.mouseClicked(mx, my, btn);
        }

        // Right-click → start dragging
        if (btn == 1) {
            lastMouseX = mx;
            lastMouseY = my;
            dragging = true;
            return true;
        }

        // Left-click → attempt unlocking a skill
        if (btn == 0) {
            double ax = mx - scrollX;
            double ay = my - scrollY;
            Player player = Minecraft.getInstance().player;
            if (player == null) return super.mouseClicked(mx, my, btn);

            int pts = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);
            for (SkillNode n : nodeMap.values()) {
                if (n.isMouseOver(ax, ay)) {
                    boolean unlocked = SkillData.isUnlocked(n.skill);
                    boolean available = SkillData.isAvailable(n.skill);
                    if (!unlocked && available && pts > 0) {
                        PacketDistributor
                                .sendToServer(new UnlockSkillPacket(n.skill, UnlockSkillPacket.Success.SUCCESS));
                        player.setData(ModAttachments.AVAILABLE_SKILL_POINTS, pts - 1);
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
            scrollX += (int) (mx - lastMouseX);
            scrollY += (int) (my - lastMouseY);
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 1 && dragging) {
            dragging = false;
            return true;
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

    /** Simple parent→child link, used for drawing connectors */
    private static class Connection {
        final SkillNode parent, child;

        Connection(SkillNode p, SkillNode c) {
            this.parent = p;
            this.child = c;
        }
    }

    /** One node in the GUI, at (x,y) with a 32×32 box and icon. */
    @OnlyIn(Dist.CLIENT)
    private static class SkillNode {
        final int x, y;
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

            boolean unlocked = SkillData.isUnlocked(skill);
            boolean available = SkillData.isAvailable(skill);
            int pts = player.getData(ModAttachments.AVAILABLE_SKILL_POINTS);

            // Choose background color
            int bgColor;
            if (unlocked) {
                bgColor = COLOR_NODE_BG_UNLOCKED;
            } else if (available && pts > 0) {
                bgColor = COLOR_NODE_BG_AVAILABLE;
            } else {
                bgColor = COLOR_NODE_BG_LOCKED;
            }

            int borderColor = hover ? COLOR_NODE_BORDER_HIGHLIGHT : COLOR_NODE_BORDER_NORMAL;

            // Draw the box
            gui.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, bgColor);
            gui.hLine(x, x + NODE_SIZE - 1, y, borderColor);
            gui.hLine(x, x + NODE_SIZE - 1, y + NODE_SIZE - 1, borderColor);
            gui.vLine(x, y, y + NODE_SIZE - 1, borderColor);
            gui.vLine(x + NODE_SIZE - 1, y, y + NODE_SIZE - 1, borderColor);

            // Draw the icon in the center
            RenderSystem.enableBlend();
            int pad = 4;
            gui.blit(
                    icon,
                    x + pad,
                    y + pad,
                    0, 0,
                    NODE_SIZE - pad * 2,
                    NODE_SIZE - pad * 2,
                    NODE_SIZE - pad * 2,
                    NODE_SIZE - pad * 2
            );
            RenderSystem.disableBlend();

            // If locked & not available, dark overlay
            if (!unlocked && !available) {
                gui.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, 0x99000000);
            }
        }

        boolean isMouseOver(double mx, double my) {
            return mx >= x && mx < x + NODE_SIZE && my >= y && my < y + NODE_SIZE;
        }
    }
}
