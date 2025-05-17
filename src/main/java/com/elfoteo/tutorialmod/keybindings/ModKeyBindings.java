package com.elfoteo.tutorialmod.keybindings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
@OnlyIn(Dist.CLIENT)
public class ModKeyBindings {
    public static KeyMapping CLOAK_KEY;
    public static KeyMapping VISOR_KEY;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        CLOAK_KEY = new KeyMapping(
                "key.tutorialmod.cloak",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.tutorialmod"
        );
        event.register(CLOAK_KEY);

        VISOR_KEY = new KeyMapping(
                "key.tutorialmod.visor",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,       // default to X
                "key.categories.tutorialmod"
        );
        event.register(VISOR_KEY);
    }
}
