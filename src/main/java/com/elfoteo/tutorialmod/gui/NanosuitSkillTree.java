package com.elfoteo.tutorialmod.gui;

import com.elfoteo.tutorialmod.attachments.ModAttachments;
import com.elfoteo.tutorialmod.network.custom.skills.GetAllSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.ResetSkillsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.SkillPointsPacket;
import com.elfoteo.tutorialmod.network.custom.skills.UnlockSkillPacket;
import com.elfoteo.tutorialmod.skill.Skill;
import com.elfoteo.tutorialmod.skill.SkillData;
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

import java.awt.Point;
import java.util.*;

/**
 * A skill-tree GUI that "flows" its nodes horizontally by depth and stacks each independent branch
 * vertically. No more hard-coded (x,y) in the Skill enum—everything is computed at runtime.
 */
@OnlyIn(Dist.CLIENT)
public class NanosuitSkillTree extends Screen {
    // ---- Constants ----
    private static final int NODE_SIZE       = 32;
    private static final int TITLE_HEIGHT    = 25;
    private static final int FOOTER_HEIGHT   = 40;

    // Colors
    private static final int COLOR_BG                  = 0xFF1A1A1A;
    private static final int COLOR_NODE_BG_UNLOCKED    = 0xFF0A6E4D;
    private static final int COLOR_NODE_BG_LOCKED      = 0xFF333333;
    private static final int COLOR_NODE_BG_AVAILABLE   = 0xFF555555;
    private static final int COLOR_NODE_BORDER_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_NODE_BORDER_NORMAL  = 0xFF858585;
    private static final int COLOR_TITLE_BG            = 0xFF222222;
    private static final int COLOR_FOOTER_BG           = 0xFF222222;
    private static final int COLOR_LINE_LOCKED         = 0xFF555555;
    private static final int COLOR_LINE_UNLOCKED       = 0xFF00FFFF;
    private static final int COLOR_LINE_BORDER         = 0xFF000000;

    // How far apart to place each depth-layer horizontally
    private static final int H_SPACING = 100;

    // How far apart to stack nodes vertically (within one subtree)
    private static final int V_SPACING = 50;

    // UI state
    private int scrollX, scrollY;
    private double lastMouseX, lastMouseY;
    private boolean dragging    = false;
    private boolean initialized = false;

    // One SkillNode per Skill, built at init()
    private final Map<Skill, SkillNode> nodeMap      = new HashMap<>();
    private final List<Connection>        connections = new ArrayList<>();

    // We store the computed (x,y) for each skill here after we run computeSkillCoordinates()
    private Map<Skill, Point> skillCoords;

    public NanosuitSkillTree() {
        super(Component.translatable("gui.tutorialmod.skilltree"));
    }

    @Override
    protected void init() {
        super.init();

        // 1) Ask server for current skill data
        PacketDistributor.sendToServer(new SkillPointsPacket(0, 0));
        PacketDistributor.sendToServer(new GetAllSkillsPacket(new HashMap<>()));

        if (!initialized) {
            // 2) Compute all (x,y) positions in one go:
            skillCoords = computeSkillCoordinates();

            // 3) Build each SkillNode from those coords
            for (Skill s : Skill.values()) {
                Point p = skillCoords.get(s);
                nodeMap.put(s, new SkillNode(s, p.x, p.y));
            }

            // 4) Build connections (parent → child) so we can draw lines
            for (Skill child : Skill.values()) {
                for (Skill parent : child.getParents()) {
                    SkillNode pNode = nodeMap.get(parent);
                    SkillNode cNode = nodeMap.get(child);
                    connections.add(new Connection(pNode, cNode));
                }
            }

            // 5) Finally, center everything on-screen
            computeInitialScroll();
            initialized = true;
        }

        // "Reset" button at bottom-right
        addRenderableWidget(
                Button.builder(Component.translatable("gui.tutorialmod.reset"), btn -> onReset())
                        .pos(width - 110, height - 30)
                        .size(100, 20)
                        .build()
        );

        // "Back" button at bottom-left
        addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), btn -> onClose())
                        .pos(10, height - 30)
                        .size(60, 20)
                        .build()
        );

        // "X" close in top-right
        addRenderableWidget(
                Button.builder(Component.literal("X"), btn -> onClose())
                        .pos(width - 20, 5)
                        .size(20, 20)
                        .build()
        );
    }

    /**
     * Completely redesigned coordinate computation algorithm to properly handle complex skill trees.
     * This approach:
     * 1. Computes depths for all skills (taking max path length if multiple paths exist)
     * 2. Assigns horizontal positions based on depth
     * 3. Assigns vertical positions to create proper branching structures
     * 4. Handles multi-parent nodes by ensuring proper spacing
     */
    private Map<Skill, Point> computeSkillCoordinates() {
        // Create a map to store the final coordinates
        Map<Skill, Point> coords = new EnumMap<>(Skill.class);

        // Build parent->child and child->parent maps for easier traversal
        Map<Skill, List<Skill>> childrenMap = buildChildrenMap();
        Map<Skill, List<Skill>> parentMap = buildParentMap();

        // Find all root skills (skills with no parents)
        List<Skill> roots = new ArrayList<>();
        for (Skill s : Skill.values()) {
            if (s.getParents().length == 0) {
                roots.add(s);
            }
        }

        // Sort roots alphabetically for consistent layout
        roots.sort(Comparator.comparing(Skill::getTitle));

        // Step 1: Compute optimal depths for all skills (longest path from any root)
        Map<Skill, Integer> depths = computeOptimalDepths(roots, childrenMap);

        // Step 2: Group skills by their depth level
        Map<Integer, List<Skill>> skillsByDepth = new HashMap<>();
        for (Skill s : Skill.values()) {
            int depth = depths.get(s);
            skillsByDepth.computeIfAbsent(depth, k -> new ArrayList<>()).add(s);
        }

        // Sort skills at each depth by title for consistency
        for (List<Skill> levelSkills : skillsByDepth.values()) {
            levelSkills.sort(Comparator.comparing(Skill::getTitle));
        }

        // Step 3: Assign x-coordinates based on depth
        for (Skill s : Skill.values()) {
            int depth = depths.get(s);
            int x = depth * H_SPACING;
            // Initialize with temporary y = 0, will be adjusted later
            coords.put(s, new Point(x, 0));
        }

        // Step 4: Compute proper y-coordinates using a branch-aware layout algorithm
        int maxDepth = skillsByDepth.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        assignVerticalPositions(roots, childrenMap, parentMap, coords, depths, maxDepth);

        // Step 5: Post-process to resolve any remaining overlaps
        resolveVerticalOverlaps(coords, depths);

        return coords;
    }


    /**
     * Builds a map of parent -> children relationships
     */
    private Map<Skill, List<Skill>> buildChildrenMap() {
        Map<Skill, List<Skill>> childrenMap = new EnumMap<>(Skill.class);

        // Initialize empty lists for all skills
        for (Skill s : Skill.values()) {
            childrenMap.put(s, new ArrayList<>());
        }

        // Populate children lists
        for (Skill s : Skill.values()) {
            for (Skill parent : s.getParents()) {
                childrenMap.get(parent).add(s);
            }
        }

        return childrenMap;
    }

    /**
     * Builds a map of child -> parents relationships
     */
    private Map<Skill, List<Skill>> buildParentMap() {
        Map<Skill, List<Skill>> parentMap = new EnumMap<>(Skill.class);

        // Initialize empty lists for all skills
        for (Skill s : Skill.values()) {
            parentMap.put(s, new ArrayList<>());
        }

        // Populate parent lists
        for (Skill s : Skill.values()) {
            Collections.addAll(parentMap.get(s), s.getParents());
        }

        return parentMap;
    }

    /**
     * Computes the optimal depth for each skill by finding the longest path from any root.
     * This ensures that skills with multiple parents are placed at the correct depth.
     */
    private Map<Skill, Integer> computeOptimalDepths(List<Skill> roots, Map<Skill, List<Skill>> childrenMap) {
        Map<Skill, Integer> depths = new EnumMap<>(Skill.class);

        // Initialize all depths to -1 (unvisited)
        for (Skill s : Skill.values()) {
            depths.put(s, -1);
        }

        // Process each root skill
        for (Skill root : roots) {
            // Set root to depth 0
            depths.put(root, 0);

            // BFS to compute depths, revisiting nodes if we find a longer path
            Queue<Skill> queue = new LinkedList<>();
            queue.add(root);

            while (!queue.isEmpty()) {
                Skill current = queue.poll();
                int currentDepth = depths.get(current);

                for (Skill child : childrenMap.get(current)) {
                    int childCurrentDepth = depths.get(child);
                    int newDepth = currentDepth + 1;

                    // If unvisited or we found a longer path, update depth and requeue
                    if (childCurrentDepth == -1 || newDepth > childCurrentDepth) {
                        depths.put(child, newDepth);
                        queue.add(child); // Revisit this node to propagate the change
                    }
                }
            }
        }

        return depths;
    }

    /**
     * Assigns vertical positions so that:
     *  1. All single‐parent sub‐branches fan out properly.
     *  2. Any multi‐parent node is placed exactly at the average Y of all its parents (once all parents at that depth are done).
     */
    private void assignVerticalPositions(List<Skill> roots,
                                         Map<Skill, List<Skill>> childrenMap,
                                         Map<Skill, List<Skill>> parentMap,
                                         Map<Skill, Point> coords,
                                         Map<Skill, Integer> depths,
                                         int maxDepth) {
        // Step 1: Give every root node (depth 0) an initial Y, spaced out.
        int yOffset = 0;
        for (Skill root : roots) {
            coords.put(root, new Point(coords.get(root).x, yOffset));
            yOffset += V_SPACING * 2;  // extra gap between root “stems”
        }

        // Step 2: For each depth level d = 0..maxDepth–1, place all children at depth d+1.
        for (int d = 0; d < maxDepth; d++) {
            // A. Collect all multi-parent children (depth = d+1) so we can place them in one go.
            List<Skill> multiParentKids = new ArrayList<>();

            // B. For every skill S at depth d, position its single-parent children.
            List<Skill> parentsAtD = new ArrayList<>();
            for (Skill s : Skill.values()) {
                if (depths.get(s) == d) {
                    parentsAtD.add(s);
                }
            }
            // Sort parents alphabetically so we get a deterministic order.
            parentsAtD.sort(Comparator.comparing(Skill::getTitle));

            for (Skill parent : parentsAtD) {
                List<Skill> children = childrenMap.get(parent);
                if (children.isEmpty()) {
                    continue;
                }

                // Split children into two buckets: single-parent vs. multi-parent.
                List<Skill> singleOnly = new ArrayList<>();
                for (Skill c : children) {
                    if (depths.get(c) == d + 1) {
                        if (parentMap.get(c).size() == 1) {
                            singleOnly.add(c);
                        } else {
                            multiParentKids.add(c);
                        }
                    }
                }
                // (We let multiParentKids collect duplicates; we’ll dedupe below.)

                // If there are N single-parent children, distribute them evenly under 'parent'.
                if (!singleOnly.isEmpty()) {
                    singleOnly.sort(Comparator.comparing(Skill::getTitle));
                    int total = singleOnly.size();
                    // Start Y so that siblings “fan out” symmetrically around the parent Y.
                    int parentY = coords.get(parent).y;
                    int startY = parentY - ((total - 1) * V_SPACING) / 2;

                    for (int i = 0; i < total; i++) {
                        Skill child = singleOnly.get(i);
                        int childY = startY + (i * V_SPACING);
                        coords.put(child, new Point(coords.get(child).x, childY));
                    }
                }
            }

            // C. Now dedupe our multiParentKids list and place each exactly at the average of all its parents.
            Set<Skill> dedup = new LinkedHashSet<>(multiParentKids);
            for (Skill child : dedup) {
                // Confirm that *all* of child’s parents are at depth d, so their coords are already set.
                List<Skill> pList = parentMap.get(child);
                boolean allAtD = true;
                for (Skill p : pList) {
                    if (depths.get(p) != d) {
                        allAtD = false;
                        break;
                    }
                }
                if (!allAtD) {
                    // If not every parent is at depth d, we’ll pick this up again when
                    // we hit the parent’s actual depth. (In a strictly layered DAG this rarely happens.)
                    continue;
                }

                // Compute the average Y of all parents.
                int sumY = 0;
                for (Skill p : pList) {
                    sumY += coords.get(p).y;
                }
                int avgY = sumY / pList.size();
                coords.put(child, new Point(coords.get(child).x, avgY));
            }
        }

        // Step 3: Final pass to resolve any vertical overlaps at each X (depth).
        // Group skills by their X (which is depth * H_SPACING).
        Map<Integer, List<Skill>> byX = new HashMap<>();
        for (Skill s : Skill.values()) {
            Point pt = coords.get(s);
            byX.computeIfAbsent(pt.x, k -> new ArrayList<>()).add(s);
        }
        for (int x : byX.keySet()) {
            // Sort all nodes at this X by their Y, then push any that collide downward.
            List<Skill> column = byX.get(x);
            column.sort(Comparator.comparingInt(s -> coords.get(s).y));

            for (int i = 1; i < column.size(); i++) {
                Skill prev = column.get(i - 1);
                Skill curr = column.get(i);
                Point prevP = coords.get(prev);
                Point currP = coords.get(curr);

                if (currP.y - prevP.y < V_SPACING) {
                    // Nudge curr down so it’s at least V_SPACING below prev.
                    coords.put(curr, new Point(currP.x, prevP.y + V_SPACING));
                }
            }
        }
    }

    /**
     * Post-processing step to resolve any remaining vertical overlaps between nodes.
     */
    private void resolveVerticalOverlaps(Map<Skill, Point> coords, Map<Skill, Integer> depths) {
        // Group skills by their X-coordinate (depth)
        Map<Integer, List<Skill>> skillsByX = new HashMap<>();

        for (Skill s : Skill.values()) {
            Point p = coords.get(s);
            skillsByX.computeIfAbsent(p.x, k -> new ArrayList<>()).add(s);
        }

        // For each x-coordinate, sort skills by their y-coordinate and resolve overlaps
        for (int x : skillsByX.keySet()) {
            List<Skill> skillsAtX = skillsByX.get(x);

            // Sort by Y position
            skillsAtX.sort(Comparator.comparingInt(s -> coords.get(s).y));

            // Check and fix overlaps
            for (int i = 1; i < skillsAtX.size(); i++) {
                Skill prev = skillsAtX.get(i - 1);
                Skill curr = skillsAtX.get(i);

                Point prevPos = coords.get(prev);
                Point currPos = coords.get(curr);

                // If overlap detected (less than minimum spacing)
                if (currPos.y - prevPos.y < V_SPACING) {
                    // Push current skill down
                    coords.put(curr, new Point(currPos.x, prevPos.y + V_SPACING));
                }
            }
        }
    }

    /**
     * After all nodes exist, center the bounding box of (x,y) so the skill-tree sits neatly
     * in the middle of the GUI.
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

        // Center the box [minX..maxX] × [minY..maxY] inside the screen area
        scrollX = (width - (maxX - minX)) / 2 - minX;
        scrollY = (height - TITLE_HEIGHT - FOOTER_HEIGHT - (maxY - minY)) / 2 - minY + TITLE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float pt) {
        super.render(gui, mouseX, mouseY, pt);
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

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
        int guiLeft   = 0;
        int guiTop    = TITLE_HEIGHT;
        int guiWidth  = width;
        int guiHeight = height - TITLE_HEIGHT - FOOTER_HEIGHT;
        Window window = Minecraft.getInstance().getWindow();
        double scaleFactor = window.getGuiScale();
        int winW = window.getWidth();
        int winH = window.getHeight();
        int scissorX = (int)(guiLeft * scaleFactor);
        int scissorY = (int)(winH - (guiTop + guiHeight) * scaleFactor);
        int scissorW = (int)(guiWidth * scaleFactor);
        int scissorH = (int)(guiHeight * scaleFactor);
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

            boolean unlocked  = SkillData.isUnlocked(hoveredNode.skill);
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
        int x2 = c.child.x  + NODE_SIZE / 2;
        int y2 = c.child.y  + NODE_SIZE / 2;

        // Color depends on whether the parent skill is unlocked
        boolean parentUnlocked = SkillData.isUnlocked(c.parent.skill);
        int lineColor = parentUnlocked ? COLOR_LINE_UNLOCKED : COLOR_LINE_LOCKED;

        // Draw a better L-shaped connector with intermediate point
        int midX = x2;
        int midY = y1;

        // Draw horizontal segment (parent to bend point)
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1 - 1, COLOR_LINE_BORDER);
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1, lineColor);
        gui.hLine(Math.min(x1, midX), Math.max(x1, midX), y1 + 1, COLOR_LINE_BORDER);

        // Draw vertical segment (bend point to child)
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
                    boolean unlocked  = SkillData.isUnlocked(n.skill);
                    boolean available = SkillData.isAvailable(n.skill);
                    if (!unlocked && available && pts > 0) {
                        PacketDistributor.sendToServer(new UnlockSkillPacket(n.skill, UnlockSkillPacket.Success.SUCCESS));
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
            scrollX    += (int)(mx - lastMouseX);
            scrollY    += (int)(my - lastMouseY);
            lastMouseX  = mx;
            lastMouseY  = my;
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
            this.child  = c;
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
            this.x     = x;
            this.y     = y;
            this.icon  = ResourceLocation.fromNamespaceAndPath(
                    "tutorialmod",
                    "textures/gui/" + s.getIconPath() + ".png"
            );
        }

        void render(GuiGraphics gui, int mx, int my) {
            boolean hover = isMouseOver(mx, my);
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            boolean unlocked  = SkillData.isUnlocked(skill);
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
            gui.hLine(x, x + NODE_SIZE - 1,     y,              borderColor);
            gui.hLine(x, x + NODE_SIZE - 1,     y + NODE_SIZE - 1, borderColor);
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
                    NODE_SIZE - pad*2,
                    NODE_SIZE - pad*2,
                    NODE_SIZE - pad*2,
                    NODE_SIZE - pad*2
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