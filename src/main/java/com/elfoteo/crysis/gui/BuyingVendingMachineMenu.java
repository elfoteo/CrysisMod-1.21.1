// -------------------------------------------------------------
// BuyingVendingMachineMenu.java
// -------------------------------------------------------------
package com.elfoteo.crysis.gui;

import com.elfoteo.crysis.block.ModBlocks;
import com.elfoteo.crysis.block.entity.CreativeVendingMachineBlockEntity;
import com.elfoteo.crysis.screen.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

public class BuyingVendingMachineMenu extends AbstractContainerMenu {
    public final CreativeVendingMachineBlockEntity blockEntity;
    private final Level level;

    public static final int INPUT_SLOT_COUNT  = 6;
    public static final int OUTPUT_SLOT_COUNT = 6;
    public static final int TOTAL_SLOT_COUNT  = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT; // 12

    public static final int LEFT_INPUT_X   = 15;
    public static final int LEFT_OUTPUT_X  = LEFT_INPUT_X + 18 + 28;   // 61
    public static final int RIGHT_INPUT_X  = LEFT_OUTPUT_X + 18 + 18;  // 97
    public static final int RIGHT_OUTPUT_X = RIGHT_INPUT_X + 18 + 28;  // 143

    public static final int[] Y_POSITIONS = { 18, 43, 68 };

    private static final int HOTBAR_SLOT_COUNT             = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT    = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT   = PLAYER_INVENTORY_ROW_COUNT * PLAYER_INVENTORY_COLUMN_COUNT;
    public  static final int VANILLA_SLOT_COUNT            = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT; // 36
    public  static final int VANILLA_FIRST_SLOT_INDEX      = 0;

    /**
     * Encapsulates one “trade”:
     *  - handlerInputIndex, handlerOutputIndex (0..11 in the TE’s ItemStackHandler)
     *  - slotInputIndex, slotOutputIndex (where in this.slots those Slot objects live)
     *  - on‐screen X/Y for drawing them
     */
    public static class Trade {
        public final int handlerInputIndex;
        public final int handlerOutputIndex;
        public final int slotInputIndex;
        public final int slotOutputIndex;
        public final int inputX, inputY;
        public final int outputX, outputY;

        public Trade(int handlerInputIndex,
                     int handlerOutputIndex,
                     int slotInputIndex,
                     int slotOutputIndex,
                     int inputX, int inputY,
                     int outputX, int outputY)
        {
            this.handlerInputIndex  = handlerInputIndex;
            this.handlerOutputIndex = handlerOutputIndex;
            this.slotInputIndex     = slotInputIndex;
            this.slotOutputIndex    = slotOutputIndex;
            this.inputX  = inputX;
            this.inputY  = inputY;
            this.outputX = outputX;
            this.outputY = outputY;
        }
    }

    private final List<Trade> trades = new ArrayList<>();

    public BuyingVendingMachineMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public BuyingVendingMachineMenu(int containerId, Inventory inv, BlockEntity be) {
        super(ModMenuTypes.BUYING_VENDING_MACHINE_MENU.get(), containerId);
        this.blockEntity = (CreativeVendingMachineBlockEntity) be;
        this.level = inv.player.level();

        // 1) Add player inventory & hotbar
        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        // 2) Add TE slots, but skip any “entirely empty” trade (both price & output empty)
        ItemStackHandler handler = (ItemStackHandler) this.blockEntity.inventory;

        for (int row = 0; row < Y_POSITIONS.length; row++) {
            int y = Y_POSITIONS[row];

            // ── LEFT TRADE ──
            int handlerLeftInput   = row;       // 0,1,2
            int handlerLeftOutput  = row + 6;   // 6,7,8

            ItemStack leftPriceStack  = handler.getStackInSlot(handlerLeftInput);
            ItemStack leftOutputStack = handler.getStackInSlot(handlerLeftOutput);

            if (!leftPriceStack.isEmpty() || !leftOutputStack.isEmpty()) {
                // Add input slot
                Slot inputSlotLeft = new ReadOnlyInputSlot(
                        handler,
                        handlerLeftInput,
                        LEFT_INPUT_X, y
                );
                this.addSlot(inputSlotLeft);
                int slotIndexLeftIn  = this.slots.size() - 1;

                // Add output slot
                Slot outputSlotLeft = new PurchaseOutputSlot(
                        handler,
                        handlerLeftOutput,
                        LEFT_OUTPUT_X, y,
                        handlerLeftInput
                );
                this.addSlot(outputSlotLeft);
                int slotIndexLeftOut = this.slots.size() - 1;

                trades.add(new Trade(
                        handlerLeftInput,
                        handlerLeftOutput,
                        slotIndexLeftIn,
                        slotIndexLeftOut,
                        LEFT_INPUT_X, y,
                        LEFT_OUTPUT_X, y
                ));
            }

            // ── RIGHT TRADE ──
            int handlerRightInput  = row + 3;   // 3,4,5
            int handlerRightOutput = row + 9;   // 9,10,11

            ItemStack rightPriceStack  = handler.getStackInSlot(handlerRightInput);
            ItemStack rightOutputStack = handler.getStackInSlot(handlerRightOutput);

            if (!rightPriceStack.isEmpty() || !rightOutputStack.isEmpty()) {
                // Add input slot
                Slot inputSlotRight = new ReadOnlyInputSlot(
                        handler,
                        handlerRightInput,
                        RIGHT_INPUT_X, y
                );
                this.addSlot(inputSlotRight);
                int slotIndexRightIn  = this.slots.size() - 1;

                // Add output slot
                Slot outputSlotRight = new PurchaseOutputSlot(
                        handler,
                        handlerRightOutput,
                        RIGHT_OUTPUT_X, y,
                        handlerRightInput
                );
                this.addSlot(outputSlotRight);
                int slotIndexRightOut = this.slots.size() - 1;

                trades.add(new Trade(
                        handlerRightInput,
                        handlerRightOutput,
                        slotIndexRightIn,
                        slotIndexRightOut,
                        RIGHT_INPUT_X, y,
                        RIGHT_OUTPUT_X, y
                ));
            }
        }
    }

    /** Expose the active trades so the screen can render them. */
    public List<Trade> getTrades() {
        return trades;
    }

    /**
     * SHIFT+CLICK support: if the clicked index is one of our output slots,
     * perform a bulk purchase up to one full stack (or as many as the player can afford).
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        for (Trade trade : trades) {
            if (trade.slotOutputIndex == index) {
                ItemStackHandler teHandler = (ItemStackHandler) this.blockEntity.inventory;
                ItemStack priceTemplate   = teHandler.getStackInSlot(trade.handlerInputIndex);
                ItemStack outputTemplate  = teHandler.getStackInSlot(trade.handlerOutputIndex);

                if (outputTemplate.isEmpty()) {
                    return ItemStack.EMPTY;
                }

                int priceCount       = priceTemplate.getCount();   // cost per trade
                int outputCount      = outputTemplate.getCount();  // output per trade
                if (outputCount <= 0) outputCount = 1;

                // 1) How many trades can the player afford?
                int totalInPlayer = (priceCount > 0)
                        ? countInPlayer(player, priceTemplate)
                        : Integer.MAX_VALUE;  // free trades

                int tradeCount = (priceCount > 0)
                        ? totalInPlayer / priceCount
                        : (outputTemplate.getMaxStackSize() / outputCount);

                if (tradeCount <= 0) {
                    return ItemStack.EMPTY;
                }

                // 2) Remove exactly priceCount * tradeCount items:
                if (priceCount > 0) {
                    removeFromPlayer(player, priceTemplate, priceCount * tradeCount);
                }

                // 3) Compute total items to give:
                int totalToGive = outputCount * tradeCount;
                int maxStack   = outputTemplate.getMaxStackSize();

                // 4) Split into stacks of up to maxStack and hand out:
                while (totalToGive > 0) {
                    int giveNow = Math.min(totalToGive, maxStack);
                    ItemStack toGive = outputTemplate.copy();
                    toGive.setCount(giveNow);

                    if (!player.getInventory().add(toGive)) {
                        player.drop(toGive, false);
                    }
                    totalToGive -= giveNow;
                }

                return ItemStack.EMPTY;
            }
        }

        // Not one of our output slots → default behavior
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                player,
                ModBlocks.CREATIVE_VENDING_MACHINE.get()
        );
    }

    private void addPlayerInventory(Inventory playerInventory) {
        int startY = 101;
        for (int row = 0; row < PLAYER_INVENTORY_ROW_COUNT; ++row) {
            for (int col = 0; col < PLAYER_INVENTORY_COLUMN_COUNT; ++col) {
                int x = 8 + col * 18;
                int y = startY + row * 18;
                this.addSlot(new Slot(
                        playerInventory,
                        col + row * PLAYER_INVENTORY_COLUMN_COUNT + HOTBAR_SLOT_COUNT,
                        x, y
                ));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        int hotbarY = 159;
        for (int col = 0; col < HOTBAR_SLOT_COUNT; ++col) {
            int x = 8 + col * 18;
            this.addSlot(new Slot(playerInventory, col, x, hotbarY));
        }
    }

    /** Read‐only slot for the price (input) side. */
    public static class ReadOnlyInputSlot extends SlotItemHandler {
        public ReadOnlyInputSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player)     { return false; }
    }

    /**
     * Output slot that, when clicked, performs a single purchase (or free if priceCount==0).
     * SHIFT+CLICK is already handled via quickMoveStack(...).
     */
    public static class PurchaseOutputSlot extends SlotItemHandler {
        private final int inputIndex;

        public PurchaseOutputSlot(ItemStackHandler handler,
                                  int index,
                                  int x, int y,
                                  int inputIndex)
        {
            super(handler, index, x, y);
            this.inputIndex = inputIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // cannot place into output
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStackHandler teHandler = (ItemStackHandler) this.getItemHandler();
            ItemStack priceStack = teHandler.getStackInSlot(inputIndex);
            int priceCount = priceStack.getCount();

            // If priceCount == 0, item is free → can always pick one
            if (priceCount <= 0) {
                return true;
            }

            int totalInPlayer = countInPlayer(player, priceStack);
            return totalInPlayer >= priceCount;
        }

        @Override
        public ItemStack getItem() {
            ItemStack template = super.getItem();
            return template.isEmpty() ? ItemStack.EMPTY : template.copy();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack template = super.getItem();
            return template.isEmpty() ? ItemStack.EMPTY : template.copy();
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            ItemStackHandler teHandler = (ItemStackHandler) this.getItemHandler();
            ItemStack priceStack = teHandler.getStackInSlot(inputIndex);
            int priceCount = priceStack.getCount();

            // If priceCount > 0, remove that many items from the player's inventory
            if (priceCount > 0) {
                removeFromPlayer(player, priceStack, priceCount);
            }
            // Otherwise, the item was free, so do nothing further.
        }
    }

    static int countInPlayer(Player player, ItemStack priceStack) {
        int count = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && ItemStack.isSameItem(s, priceStack)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private static void removeFromPlayer(Player player, ItemStack priceStack, int toRemove) {
        for (ItemStack s : player.getInventory().items) {
            if (toRemove <= 0) break;
            if (!s.isEmpty() && ItemStack.isSameItem(s, priceStack)) {
                int taken = Math.min(s.getCount(), toRemove);
                s.shrink(taken);
                toRemove -= taken;
            }
        }
    }
}
