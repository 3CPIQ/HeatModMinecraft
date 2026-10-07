package com.xoy.heatmod.client;

import com.xoy.heatmod.HeatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class HeatClientModEvents {
    private HeatClientModEvents() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("heat_resistance", (gui, graphics, partialTick, width, height) -> {
            if (!ClientHeatState.active) return;

            float normalized = Mth.clamp(ClientHeatState.heat / 100.0F, 0.0F, 1.0F);
            int alpha = 16 + (int)((1.0F - normalized) * 30.0F);
            graphics.fill(0, 0, width, height, (alpha << 24) | 0x00FF6500);

            int barWidth = 170;
            int barHeight = 7;
            int x = (width - barWidth) / 2;
            int y = 10;
            graphics.fill(x - 2, y - 2, x + barWidth + 2, y + barHeight + 2, 0xB0000000);
            graphics.fill(x, y, x + barWidth, y + barHeight, 0xAA3A1300);
            graphics.fill(x, y, x + Math.round(barWidth * normalized), y + barHeight, 0xFFFF7A18);

            Font font = Minecraft.getInstance().font;
            String label = "HEAT RESISTANCE  " + Math.round(ClientHeatState.heat) + "%";
            graphics.drawCenteredString(font, label, width / 2, y + 10, 0xFFFFA24A);
        });
    }
}

@Mod.EventBusSubscriber(modid = HeatMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
final class HeatClientForgeEvents {
    private HeatClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ClientHeatState.tick();
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ClientHeatState.shakeTicks <= 0 || ClientHeatState.shakeStrength <= 0.001F) return;
        double t = (System.nanoTime() / 1_000_000.0) * 0.045;
        float s = ClientHeatState.shakeStrength;
        event.setPitch(event.getPitch() + (float)Math.sin(t * 1.31) * s);
        event.setYaw(event.getYaw() + (float)Math.sin(t * 0.97 + 1.7) * s * 0.65F);
        event.setRoll(event.getRoll() + (float)Math.sin(t * 1.73 + 0.6) * s * 1.3F);
    }
}
