package com.elfoteo.crysis.particle;
import com.elfoteo.crysis.CrysisMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister
            .create(BuiltInRegistries.PARTICLE_TYPE, CrysisMod.MOD_ID);
    public static final Supplier<SimpleParticleType> BISMUTH_PARTICLES = PARTICLE_TYPES.register("bismuth_particles",
            () -> new SimpleParticleType(true));
    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
