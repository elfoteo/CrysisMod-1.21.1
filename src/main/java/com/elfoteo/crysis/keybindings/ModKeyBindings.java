package com.elfoteo.crysis.keybindings;

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
    public static KeyMapping SKILLTREE_KEY;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        CLOAK_KEY = new KeyMapping(
                "key.crysis.cloak",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.crysis");
        event.register(CLOAK_KEY);

        VISOR_KEY = new KeyMapping(
                "key.crysis.visor",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X, // default to X
                "key.categories.crysis");
        event.register(VISOR_KEY);

        SKILLTREE_KEY = new KeyMapping(
                "key.crysis.skilltree",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H, // or any key you want
                "key.categories.crysis");
        event.register(SKILLTREE_KEY);
    }
}
