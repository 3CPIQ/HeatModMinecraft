package com.xoy.heatmod.network;

import com.xoy.heatmod.client.ClientHeatState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ShakePacket(int ticks, float strength) {
    public static void encode(ShakePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ticks);
        buf.writeFloat(packet.strength);
    }

    public static ShakePacket decode(FriendlyByteBuf buf) {
        return new ShakePacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ShakePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHeatState.shake(packet.ticks, packet.strength)));
        ctx.setPacketHandled(true);
    }
}
