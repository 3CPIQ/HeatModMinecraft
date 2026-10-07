package com.xoy.heatmod.network;

import com.xoy.heatmod.client.ClientHeatState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HeatSyncPacket(boolean active, float heat) {
    public static void encode(HeatSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeFloat(packet.heat);
    }

    public static HeatSyncPacket decode(FriendlyByteBuf buf) {
        return new HeatSyncPacket(buf.readBoolean(), buf.readFloat());
    }

    public static void handle(HeatSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHeatState.setHeat(packet.active, packet.heat)));
        ctx.setPacketHandled(true);
    }
}
