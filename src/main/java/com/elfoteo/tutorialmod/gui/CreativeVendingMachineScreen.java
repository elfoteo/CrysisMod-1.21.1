// -------------------------------------------------------------
// CreativeVendingMachineScreen.java
// -------------------------------------------------------------
package com.elfoteo.tutorialmod.gui;

import com.elfoteo.tutorialmod.TutorialMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix4f;

public class CreativeVendingMachineScreen extends AbstractContainerScreen<CreativeVendingMachineMenu> {
    private static final ResourceLocation GUI_TEXTURE   = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/trades.png");
    private static final ResourceLocation SLOT_TEXTURE  = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/slot.png");
    private static final ResourceLocation ARROW_TEXTURE = ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "textures/gui/arrow_progress.png");

    public CreativeVendingMachineScreen(CreativeVendingMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        // Must set imageWidth and imageHeight before rendering begins
        this.imageWidth  = 176;
        this.imageHeight = 182;
        // Adjust inventory label position based on new imageHeight
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 1) Draw the main GUI background
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        int left = this.leftPos;
        int top  = this.topPos;
        guiGraphics.blit(GUI_TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

        // 2) Draw slot.png at each vending-machine slot position
        RenderSystem.setShaderTexture(0, SLOT_TEXTURE);
        for (int i = CreativeVendingMachineMenu.TE_INVENTORY_FIRST_SLOT_INDEX;
             i < CreativeVendingMachineMenu.TE_INVENTORY_FIRST_SLOT_INDEX + CreativeVendingMachineMenu.TE_INVENTORY_SLOT_COUNT;
             i++)
        {
            Slot slot = this.menu.slots.get(i);
            int slotX = left + slot.x - 1;
            int slotY = top  + slot.y - 1;
            // drawTexturedRect(...) parameters: gui, texture, x, y, w, h, uS, vS, r, g, b, a
            drawTexturedRect(guiGraphics, SLOT_TEXTURE, slotX, slotY, 18, 18, 1f, 1f, 1f, 1f, 1f, 1f);
        }

        // 3) Draw arrows (24×16) between each input/output pair
        //    TE slots are indexed in groups of 4 per row:
        //      Row r: baseIndex = TE_INVENTORY_FIRST_SLOT_INDEX + r*4
        //        left input  = baseIndex + 0
        //        left output = baseIndex + 1
        //        right input = baseIndex + 2
        //        right output= baseIndex + 3
        for (int row = 0; row < CreativeVendingMachineMenu.Y_POSITIONS.length; row++) {
            int baseIndex = CreativeVendingMachineMenu.TE_INVENTORY_FIRST_SLOT_INDEX + row * 4;

            // Left‐column arrow: between slot at baseIndex+0 (input) and baseIndex+1 (output)
            Slot leftInputSlot = this.menu.slots.get(baseIndex + 0);
            int leftInputX = left + leftInputSlot.x;
            int leftInputY = top  + leftInputSlot.y;
            // Arrow x = leftInputX + 18 + (28 - 24)/2 = leftInputX + 18 + 2 = leftInputX + 20
            int arrowLeftX = leftInputX + 20;
            // Arrow y = leftInputY + (18 - 16)/2 = leftInputY + 1
            int arrowLeftY = leftInputY + 1;
            drawTexturedRect(guiGraphics, ARROW_TEXTURE, arrowLeftX, arrowLeftY, 24, 16, 1f, 1f, 1f, 1f, 1f, 1f);

            // Right‐column arrow: between slot at baseIndex+2 (input) and baseIndex+3 (output)
            Slot rightInputSlot = this.menu.slots.get(baseIndex + 2);
            int rightInputX = left + rightInputSlot.x;
            int rightInputY = top  + rightInputSlot.y;
            int arrowRightX = rightInputX + 20;
            int arrowRightY = rightInputY + 1;
            drawTexturedRect(guiGraphics, ARROW_TEXTURE, arrowRightX, arrowRightY, 24, 16, 1f, 1f, 1f, 1f, 1f, 1f);
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
