package com.elfoteo.tutorialmod.gui;

import com.elfoteo.tutorialmod.block.ModBlocks;
import com.elfoteo.tutorialmod.block.entity.CreativeVendingMachineBlockEntity;
import com.elfoteo.tutorialmod.screen.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CreativeVendingMachineMenu extends AbstractContainerMenu {
    public final CreativeVendingMachineBlockEntity blockEntity;
    private final Level level;

    public static final int INPUT_SLOT_COUNT = 6;
    public static final int OUTPUT_SLOT_COUNT = 6;
    public static final int TOTAL_SLOT_COUNT = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT;

    // GUI width is 176. Each trade takes 18 (input) + 28 (arrow) + 18 (output) = 64px.
    // Two columns and 18px gap = 64 + 18 + 64 = 146px
    // Centered startX = (176 - 146) / 2 = 15
    public static final int LEFT_INPUT_X = 15;
    public static final int LEFT_OUTPUT_X = LEFT_INPUT_X + 18 + 28; // 61
    public static final int RIGHT_INPUT_X = LEFT_OUTPUT_X + 18 + 18; // 97
    public static final int RIGHT_OUTPUT_X = RIGHT_INPUT_X + 18 + 28; // 143

    // Y positions of each trade row (shifted down by 8px from original)
    public static final int[] Y_POSITIONS = {18, 43, 68};

    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_ROW_COUNT * PLAYER_INVENTORY_COLUMN_COUNT;
    public static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    public static final int VANILLA_FIRST_SLOT_INDEX = 0;
    public static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;
    public static final int TE_INVENTORY_SLOT_COUNT = TOTAL_SLOT_COUNT;

    public CreativeVendingMachineMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public CreativeVendingMachineMenu(int containerId, Inventory inv, BlockEntity blockEntity) {
        super(ModMenuTypes.CREATIVE_VENDING_MACHINE_MENU.get(), containerId);
        this.blockEntity = (CreativeVendingMachineBlockEntity) blockEntity;
        this.level = inv.player.level();

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        for (int row = 0; row < Y_POSITIONS.length; row++) {
            int y = Y_POSITIONS[row];

            int leftInputIndex = row;
            int leftOutputIndex = row + 6;
            addSlot(new SlotItemHandler(this.blockEntity.inventory, leftInputIndex, LEFT_INPUT_X, y));
            addSlot(new SlotItemHandler(this.blockEntity.inventory, leftOutputIndex, LEFT_OUTPUT_X, y));

            int rightInputIndex = row + 3;
            int rightOutputIndex = row + 9;
            addSlot(new SlotItemHandler(this.blockEntity.inventory, rightInputIndex, RIGHT_INPUT_X, y));
            addSlot(new SlotItemHandler(this.blockEntity.inventory, rightOutputIndex, RIGHT_OUTPUT_X, y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        if (index < VANILLA_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.CREATIVE_VENDING_MACHINE.get());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        int startY = 101;
        for (int row = 0; row < PLAYER_INVENTORY_ROW_COUNT; ++row) {
            for (int col = 0; col < PLAYER_INVENTORY_COLUMN_COUNT; ++col) {
                int x = 8 + col * 18;
                int y = startY + row * 18;
                this.addSlot(new Slot(playerInventory, col + row * PLAYER_INVENTORY_COLUMN_COUNT + HOTBAR_SLOT_COUNT, x, y));
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
}
