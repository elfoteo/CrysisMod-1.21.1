package com.elfoteo.crysis;
import com.elfoteo.crysis.attachments.AttachmentSyncing;
import com.elfoteo.crysis.attachments.ModAttachments;
import com.elfoteo.crysis.block.entity.renderer.CreativeVendingMachineBlockEntityRenderer;
import com.elfoteo.crysis.block.entity.renderer.FlagBlockEntityRenderer;
import com.elfoteo.crysis.commands.ModCommands;
import com.elfoteo.crysis.event.PowerJumpUpgrade;
import com.elfoteo.crysis.event.PowerJumpUpgradeClient;
import com.elfoteo.crysis.gui.BuyingVendingMachineScreen;
import com.elfoteo.crysis.gui.CreativeVendingMachineScreen;
import com.elfoteo.crysis.nanosuit.NanosuitUpgrades;
import com.elfoteo.crysis.nanosuit.RegenerationSystem;
import com.elfoteo.crysis.network.ModPackets;
import com.elfoteo.crysis.block.ModBlocks;
import com.elfoteo.crysis.block.entity.ModBlockEntities;
import com.elfoteo.crysis.component.ModDataComponents;
import com.elfoteo.crysis.effect.ModEffects;
import com.elfoteo.crysis.enchantment.ModEnchantmentEffects;
import com.elfoteo.crysis.entity.ModEntities;
import com.elfoteo.crysis.item.ModCreativeModeTabs;
import com.elfoteo.crysis.item.ModItems;
import com.elfoteo.crysis.keybindings.ModKeyBindings;
import com.elfoteo.crysis.loot.ModLootModifiers;
import com.elfoteo.crysis.nanosuit.Nanosuit;
import com.elfoteo.crysis.particle.BismuthParticles;
import com.elfoteo.crysis.particle.ModParticles;
import com.elfoteo.crysis.potion.ModPotions;
import com.elfoteo.crysis.recipe.ModRecipes;
import com.elfoteo.crysis.screen.ModMenuTypes;
import com.elfoteo.crysis.sound.ModSounds;
import com.elfoteo.crysis.util.ModItemProperties;
import com.elfoteo.crysis.util.InfraredShader;
import com.elfoteo.crysis.util.TrailTextureManager;
import com.elfoteo.crysis.villager.ModVillagers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CrysisMod.MOD_ID)
public class CrysisMod {
    public static final String MOD_ID = "crysis";
    private static final Logger LOGGER = LogUtils.getLogger();
    // The constructor for the mod class is the first code that is run when your mod
    // is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and
    // pass them in automatically.
    public CrysisMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod)
        // to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in
        // this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(ScoreboardUpdater.class);
        NeoForge.EVENT_BUS.register(Nanosuit.class);
        NeoForge.EVENT_BUS.register(NanosuitUpgrades.class);
        NeoForge.EVENT_BUS.register(RegenerationSystem.class);
        NeoForge.EVENT_BUS.register(PowerJumpUpgrade.class);
        NeoForge.EVENT_BUS.register(AttachmentSyncing.class);
        if (FMLLoader.getDist() == Dist.CLIENT){
            modEventBus.register(InfraredShader.class);
            NeoForge.EVENT_BUS.register(PowerJumpUpgradeClient.class);
            NeoForge.EVENT_BUS.register(TrailTextureManager.class);
        }

        ModCreativeModeTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEffects.register(modEventBus);
        ModPotions.register(modEventBus);
        ModEnchantmentEffects.register(modEventBus);
        ModEntities.register(modEventBus);
        ModVillagers.register(modEventBus);
        ModParticles.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModAttachments.register(modEventBus);
        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
        // Register our mod's ModConfigSpec so that FML can create and load the config
        // file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
    private void commonSetup(final FMLCommonSetupEvent event) {
    }
    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        //CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
        //ModCommands.register(dispatcher);
        ScoreboardUpdater.onServerStarting(event);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }


    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class CommonModEvents {
        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            if (FMLLoader.getDist() == Dist.CLIENT) {
                ModPackets.registerClient(event);
            } else {
                // Is dedicated server
                ModPackets.registerServer(event);
            }
        }
    }
    // You can use EventBusSubscriber to automatically register all static methods
    // in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            ModItemProperties.addCustomItemProperties();
        }
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            ModKeyBindings.registerKeyMappings(event);
        }
        @SubscribeEvent
        public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.BISMUTH_PARTICLES.get(), BismuthParticles.Provider::new);
        }
        @SubscribeEvent
        public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.FLAG_BE.get(), FlagBlockEntityRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.CREATIVE_VENDING_MACHINE_BE.get(), CreativeVendingMachineBlockEntityRenderer::new);
        }
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.CREATIVE_VENDING_MACHINE_MENU.get(), CreativeVendingMachineScreen::new);
            event.register(ModMenuTypes.BUYING_VENDING_MACHINE_MENU.get(), BuyingVendingMachineScreen::new);
        }
    }
}
