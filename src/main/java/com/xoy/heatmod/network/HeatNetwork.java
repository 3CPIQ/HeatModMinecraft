package com.xoy.heatmod.network;

import com.xoy.heatmod.HeatMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HeatNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HeatMod.MODID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals
    );

    private HeatNetwork() {}

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, HeatSyncPacket.class, HeatSyncPacket::encode, HeatSyncPacket::decode, HeatSyncPacket::handle);
        CHANNEL.registerMessage(id, ShakePacket.class, ShakePacket::encode, ShakePacket::decode, ShakePacket::handle);
    }
}
