// -------------------------------------------------------------
// BuyingVendingMachineScreen.java
// -------------------------------------------------------------
package com.elfoteo.crysis.gui;

import com.elfoteo.crysis.CrysisMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.joml.Matrix4f;

public class BuyingVendingMachineScreen extends AbstractContainerScreen<BuyingVendingMachineMenu> {
    private static final ResourceLocation GUI_TEXTURE                 =
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/trades.png");
    private static final ResourceLocation SLOT_TEXTURE                =
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/slot.png");
    private static final ResourceLocation ARROW_TEXTURE               =
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/arrow_progress.png");
    private static final ResourceLocation ARROW_TOO_EXPENSIVE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CrysisMod.MOD_ID, "textures/gui/arrow_too_expensive.png");

    public BuyingVendingMachineScreen(BuyingVendingMachineMenu menu,
                                      Inventory playerInventory,
                                      Component title)
    {
        super(menu, playerInventory, title);
        this.imageWidth  = 176;
        this.imageHeight = 182;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics,
                            float partialTick,
                            int mouseX,
                            int mouseY)
    {
        // 1) Draw the main GUI background
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        int left = this.leftPos;
        int top  = this.topPos;
        guiGraphics.blit(GUI_TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

        // 2) Draw each trade only if priceStack OR outputStack is non‐empty
        ItemStackHandler handler = (ItemStackHandler) this.menu.blockEntity.inventory;

        // First bind the slot background texture
        RenderSystem.setShaderTexture(0, SLOT_TEXTURE);

        for (BuyingVendingMachineMenu.Trade trade : this.menu.getTrades()) {
            // Fetch price & output from handler
            ItemStack priceStack  = handler.getStackInSlot(trade.handlerInputIndex);
            ItemStack outputStack = handler.getStackInSlot(trade.handlerOutputIndex);

            // If both are empty (shouldn't happen, since menu skipped those),
            // skip anyway. Otherwise, the slot backgrounds and arrow are drawn.
            if (priceStack.isEmpty() && outputStack.isEmpty()) continue;

            // ── DRAW INPUT SLOT BACKGROUND (white) ──
            drawTexturedRect(guiGraphics,
                    SLOT_TEXTURE,
                    left + trade.inputX  - 1,
                    top  + trade.inputY  - 1,
                    18, 18,
                    1f, 1f,
                    1f, 1f, 1f, 1f);

            // ── DRAW OUTPUT SLOT BACKGROUND (white) ──
            drawTexturedRect(guiGraphics,
                    SLOT_TEXTURE,
                    left + trade.outputX - 1,
                    top  + trade.outputY - 1,
                    18, 18,
                    1f, 1f,
                    1f, 1f, 1f, 1f);

            // ── DRAW ARROW ──
            //
            // If price is empty, free to take. Otherwise check affordability:
            boolean canAfford = priceStack.isEmpty()
                    || BuyingVendingMachineMenu.countInPlayer(this.minecraft.player, priceStack) >= priceStack.getCount();

            ResourceLocation arrowTex = canAfford
                    ? ARROW_TEXTURE
                    : ARROW_TOO_EXPENSIVE_TEXTURE;

            int arrowX = left + trade.inputX + 20;
            int arrowY = top  + trade.inputY +  1;

            drawTexturedRect(guiGraphics,
                    arrowTex,
                    arrowX, arrowY,
                    24, 16,
                    1f, 1f,
                    1f, 1f, 1f, 1f);
        }
    }

    private static void drawTexturedRect(GuiGraphics gui,
                                         ResourceLocation tex,
                                         int x, int y, int w, int h,
                                         float uS, float vS,
                                         float r, float g, float b, float a)
    {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bindForSetup(tex);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(r, g, b, a);

        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(mat, x,     y,     0).setUv(0,    0);
        bb.addVertex(mat, x,     y + h, 0).setUv(0,    vS);
        bb.addVertex(mat, x + w, y + h, 0).setUv(uS,   vS);
        bb.addVertex(mat, x + w, y,     0).setUv(uS,   0);
        BufferUploader.drawWithShader(bb.buildOrThrow());

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void render(GuiGraphics guiGraphics,
                       int mouseX,
                       int mouseY,
                       float partialTick)
    {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
