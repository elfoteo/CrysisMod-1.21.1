package com.elfoteo.crysis.screen;
import com.elfoteo.crysis.CrysisMod;
import com.elfoteo.crysis.gui.BuyingVendingMachineMenu;
import com.elfoteo.crysis.gui.CreativeVendingMachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,
            CrysisMod.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<CreativeVendingMachineMenu>> CREATIVE_VENDING_MACHINE_MENU = registerMenuType(
            "creative_vending_machine_menu", CreativeVendingMachineMenu::new);

    public static final DeferredHolder<MenuType<?>, MenuType<BuyingVendingMachineMenu>> BUYING_VENDING_MACHINE_MENU = registerMenuType(
            "buying_vending_machine_menu", BuyingVendingMachineMenu::new);

    private static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(
            String name,
            IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }
    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
