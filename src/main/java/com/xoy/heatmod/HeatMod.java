package com.xoy.heatmod;

import com.xoy.heatmod.network.HeatNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(HeatMod.MODID)
public class HeatMod {
    public static final String MODID = "heatmod";
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> DEAD_HEAT_PULSE = SOUND_EVENTS.register("dead_heat_pulse",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "dead_heat_pulse")));

    public HeatMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUND_EVENTS.register(modBus);
        HeatNetwork.init();
        MinecraftForge.EVENT_BUS.register(this);
    }
}
