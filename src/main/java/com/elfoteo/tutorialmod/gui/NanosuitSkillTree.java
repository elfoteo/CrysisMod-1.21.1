package com.elfoteo.tutorialmod.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * An improved Nanosuit skill tree screen with a more professional look and better functionality
 */
@OnlyIn(Dist.CLIENT)
public class NanosuitSkillTree extends Screen {
    // Constants
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

    // Data
    private final List<SkillNode> nodes = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    // UI
    private Button resetButton, backButton;
    private int scrollX, scrollY;
    private double lastMouseX, lastMouseY;
    private boolean dragging = false, initialized = false;
    private SkillNode hoveredNode = null;

    // Skill points
    private static final int SKILL_POINTS = 5;
    private int availablePoints = SKILL_POINTS;

    public NanosuitSkillTree() {
        super(Component.translatable("gui.tutorialmod.skilltree"));
    }

    @Override
    protected void init() {
        super.init();

        if (!initialized) {
            // Build tree
            createSkillTree();
            updateNodeAvailability();

            // Compute bounds
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (SkillNode n : nodes) {
                minX = Math.min(minX, n.x);
                maxX = Math.max(maxX, n.x + NODE_SIZE);
                minY = Math.min(minY, n.y);
                maxY = Math.max(maxY, n.y + NODE_SIZE);
            }

            // Center tree in available area
            scrollX = (width - (maxX - minX)) / 2 - minX;
            scrollY = (height - TITLE_HEIGHT - FOOTER_HEIGHT - (maxY - minY)) / 2 - minY + TITLE_HEIGHT;
            initialized = true;
        }

        // Buttons
        resetButton = addRenderableWidget(
                Button.builder(Component.translatable("gui.tutorialmod.reset"), this::resetTree)
                        .pos(width - 110, height - 30).size(100, 20).build()
        );
        backButton = addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), b -> onClose())
                        .pos(10, height - 30).size(60, 20).build()
        );
    }

    private void createSkillTree() {
        nodes.clear();
        connections.clear();

        // Tier 1
        SkillNode strength = addNode(0, 0, "nanosuit/strength", "Strength Enhancement", "Increases melee damage and carrying capacity");
        SkillNode speed    = addNode(0, -80, "nanosuit/speed",    "Speed Enhancement",    "Improves movement and attack speed");
        SkillNode armor    = addNode(0, 80,  "nanosuit/armor",    "Armor Enhancement",    "Provides additional damage protection");

        // Tier 2
        SkillNode pAtk = addNode(-120, -40, "nanosuit/power_attack", "Power Attack", "Unleash a devastating strike on enemies");
        SkillNode jmp  = addNode(-120,  40, "nanosuit/jump",         "Jump Boost",  "Jump higher and resist fall damage");
        SkillNode dash = addNode(-120, -120,"nanosuit/dash",         "Tactical Dash","Quickly dash in any direction");
        SkillNode shld = addNode(-120,  120,"nanosuit/shield",       "Energy Shield","Generate a protective energy barrier");

        // Tier 3
        SkillNode slam = addNode(-240,   0, "nanosuit/ground_slam", "Ground Slam",     "Slam into the ground, damaging nearby enemies");
        SkillNode stea = addNode(-240,  -80,"nanosuit/stealth",     "Active Camouflage","Become partially invisible while not moving");
        SkillNode regen= addNode(-240,   80,"nanosuit/regen",       "Nano-Regeneration","Slowly regenerate health over time");

        // Connections
        addConnection(strength, pAtk);
        addConnection(strength, jmp);
        addConnection(speed, dash);
        addConnection(armor, shld);
        addConnection(pAtk, slam);
        addConnection(jmp, slam);
        addConnection(dash, stea);
        addConnection(shld, regen);

        // Make roots available immediately
        strength.available = speed.available = armor.available = true;
    }

    private SkillNode addNode(int x, int y, String iconPath, String title, String desc) {
        var node = new SkillNode(x, y,
                ResourceLocation.fromNamespaceAndPath("tutorialmod", "textures/gui/" + iconPath + ".png"),
                Component.literal(title), Component.literal(desc)
        );
        nodes.add(node);
        return node;
    }

    private void addConnection(SkillNode parent, SkillNode child) {
        connections.add(new Connection(parent, child));
        parent.children.add(child);
        child.parents.add(parent);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float pt) {
        super.render(gui, mouseX, mouseY, pt);
        renderBackground(gui, mouseX, mouseY, pt);

        // Bars
        gui.fill(0, 0, width, TITLE_HEIGHT, COLOR_TITLE_BG);
        gui.drawCenteredString(font, title, width/2, 7, 0xFFFFFFFF);
        gui.fill(0, height - FOOTER_HEIGHT, width, height, COLOR_FOOTER_BG);
        String pts = "Available Points: " + availablePoints;
        gui.drawString(font, pts, width/2 - font.width(pts)/2, height - 28, 0xFFFFFF);

        // Decide hovered node
        hoveredNode = null;
        for (SkillNode n : nodes) {
            if (n.isMouseOver(mouseX - scrollX, mouseY - scrollY)) {
                hoveredNode = n;
                break;
            }
        }

        int guiLeft = 0;
        int guiTop = TITLE_HEIGHT;
        int guiWidth = width;
        int guiHeight = height - TITLE_HEIGHT - FOOTER_HEIGHT;

        Window window = Minecraft.getInstance().getWindow();
        double scaleFactor = window.getGuiScale();

        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();

// Convert GUI coordinates to window coordinates
        int scissorX = (int) (guiLeft * scaleFactor);
        int scissorY = (int) ((windowHeight - (guiTop + guiHeight) * scaleFactor));
        int scissorWidth = (int) (guiWidth * scaleFactor);
        int scissorHeight = (int) (guiHeight * scaleFactor);

        RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);


        // Translate
        gui.pose().pushPose();
        gui.pose().translate(scrollX, scrollY, 0);

        // Draw links
        for (var c : connections) {
            drawDoubleLine(gui, c);
        }
        // Draw nodes atop
        for (var n : nodes) {
            n.render(gui, mouseX - scrollX, mouseY - scrollY);
        }
        gui.pose().popPose();
        RenderSystem.disableScissor();

        // Tooltip
        if (hoveredNode != null) {
            var tip = new ArrayList<Component>();
            tip.add(hoveredNode.title.copy().withStyle(s -> s.withBold(true)));
            tip.add(hoveredNode.description);
            if (hoveredNode.unlocked) {
                tip.add(Component.literal("Status: Unlocked").withStyle(s -> s.withColor(0x00FF00)));
            } else if (hoveredNode.available) {
                tip.add(Component.literal("Status: Available").withStyle(s -> s.withColor(0xFFFF00)));
                tip.add(Component.literal("Cost: 1 skill point"));
            } else {
                tip.add(Component.literal("Status: Locked").withStyle(s -> s.withColor(0xFF0000)));
                tip.add(Component.literal("Requires parent skills to be unlocked first"));
            }
            gui.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void drawDoubleLine(GuiGraphics gui, Connection c) {
        int x1 = c.parent.x + NODE_SIZE/2, y1 = c.parent.y + NODE_SIZE/2;
        int x2 = c.child.x + NODE_SIZE/2,  y2 = c.child.y + NODE_SIZE/2;
        int midX = x2, midY = y1;
        int mainColor = c.parent.unlocked ? COLOR_LINE_UNLOCKED : COLOR_LINE_LOCKED;

        // Horizontal segment
        // black above
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1 - 1, COLOR_LINE_BORDER);
        // main color
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1,     mainColor);
        // black below
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1 + 1, COLOR_LINE_BORDER);

        // Vertical segment
        // black left
        gui.vLine(x2 - 1, Math.min(y1, y2), Math.max(y1, y2), COLOR_LINE_BORDER);
        // main color
        gui.vLine(x2,     Math.min(y1, y2), Math.max(y1, y2), mainColor);
        // black right
        gui.vLine(x2 + 1, Math.min(y1, y2), Math.max(y1, y2), COLOR_LINE_BORDER);
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
            for (var n : nodes) {
                if (n.isMouseOver(ax, ay)) {
                    tryUnlockNode(n);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void tryUnlockNode(SkillNode n) {
        if (!n.unlocked && n.available && availablePoints > 0) {
            n.unlocked = true;
            availablePoints--;
            updateNodeAvailability();
        }
    }

    private void updateNodeAvailability() {
        for (var n : nodes) {
            if (!n.unlocked) n.available = false;
        }
        for (var n : nodes) {
            if (!n.unlocked) {
                if (n.parents.isEmpty()) {
                    n.available = true;
                } else {
                    for (var p : n.parents) {
                        if (p.unlocked) {
                            n.available = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 1 && dragging) {
            scrollX += (int)(mx - lastMouseX);
            scrollY += (int)(my - lastMouseY);
            lastMouseX = mx; lastMouseY = my;
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

    private void resetTree(Button btn) {
        for (var n : nodes) n.unlocked = false;
        availablePoints = SKILL_POINTS;
        updateNodeAvailability();
    }

    private static class Connection {
        final SkillNode parent, child;
        Connection(SkillNode p, SkillNode c) { parent = p; child = c; }
    }

    @OnlyIn(Dist.CLIENT)
    private static class SkillNode {
        final int x, y;
        final ResourceLocation icon;
        final Component title, description;
        boolean unlocked = false, available = false;
        final List<SkillNode> parents = new ArrayList<>(), children = new ArrayList<>();

        SkillNode(int x, int y, ResourceLocation icon, Component title, Component description) {
            this.x = x; this.y = y;
            this.icon = icon; this.title = title; this.description = description;
        }

        void render(GuiGraphics gui, int mx, int my) {
            boolean hover = isMouseOver(mx, my);
            int bg = unlocked ? COLOR_NODE_BG_UNLOCKED
                    : available ? COLOR_NODE_BG_AVAILABLE
                    : COLOR_NODE_BG_LOCKED;
            int border = hover ? COLOR_NODE_BORDER_HIGHLIGHT : COLOR_NODE_BORDER_NORMAL;

            // background & border
            gui.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, bg);
            gui.hLine(x, x + NODE_SIZE - 1, y, border);
            gui.hLine(x, x + NODE_SIZE - 1, y + NODE_SIZE - 1, border);
            gui.vLine(x, y, y + NODE_SIZE - 1, border);
            gui.vLine(x + NODE_SIZE - 1, y, y + NODE_SIZE - 1, border);

            // icon
            RenderSystem.enableBlend();
            int pad = 4;
            gui.blit(icon, x + pad, y + pad, 0, 0, NODE_SIZE - pad*2, NODE_SIZE - pad*2,
                    NODE_SIZE - pad*2, NODE_SIZE - pad*2);
            RenderSystem.disableBlend();

            // locked overlay
            if (!unlocked && !available) {
                gui.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, 0x99000000);
            }
        }

        boolean isMouseOver(double mx, double my) {
            return mx >= x && mx < x + NODE_SIZE && my >= y && my < y + NODE_SIZE;
        }
    }
}
