package com.xoy.heatmod.client;

import com.xoy.heatmod.HeatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
            int alpha = 42 + (int)((1.0F - normalized) * 52.0F);
            graphics.fill(0, 0, width, height, (alpha << 24) | 0x00FF5A00);

            int barWidth = 170;
            int barHeight = 7;
            int x = (width - barWidth) / 2;
            int y = 10;
            graphics.fill(x - 2, y - 2, x + barWidth + 2, y + barHeight + 2, 0xC0000000);
            graphics.fill(x, y, x + barWidth, y + barHeight, 0xCC381000);
            graphics.fill(x, y, x + Math.round(barWidth * normalized), y + barHeight, 0xFFFF7A18);

            Font font = Minecraft.getInstance().font;
            String label = "HEAT RESISTANCE  " + Math.round(ClientHeatState.heat) + "%";
            graphics.drawCenteredString(font, label, width / 2, y + 10, 0xFFFFA24A);
        });
    }
}

@Mod.EventBusSubscriber(modid = HeatMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
final class HeatClientForgeEvents {
    private static SimpleSoundInstance heatMusic;

    private HeatClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientHeatState.tick();

        Minecraft mc = Minecraft.getInstance();
        if (ClientHeatState.active) {
            if (heatMusic == null || !mc.getSoundManager().isActive(heatMusic)) {
                heatMusic = new SimpleSoundInstance(
                        new ResourceLocation(HeatMod.MODID, "dead_heat_pulse"),
                        SoundSource.MUSIC,
                        1.0F,
                        1.0F,
                        RandomSource.create(),
                        true,
                        0,
                        SoundInstance.Attenuation.NONE,
                        0.0D,
                        0.0D,
                        0.0D,
                        true
                );
                mc.getSoundManager().play(heatMusic);
            }
        } else if (heatMusic != null) {
            mc.getSoundManager().stop(heatMusic);
            heatMusic = null;
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ClientHeatState.shakeTicks <= 0 || ClientHeatState.shakeStrength <= 0.001F) return;
        double t = (System.nanoTime() / 1_000_000.0) * 0.052;
        float s = ClientHeatState.shakeStrength;
        event.setPitch(event.getPitch() + (float)Math.sin(t * 1.31) * s);
        event.setYaw(event.getYaw() + (float)Math.sin(t * 0.97 + 1.7) * s * 0.72F);
        event.setRoll(event.getRoll() + (float)Math.sin(t * 1.73 + 0.6) * s * 1.45F);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientHeatState.active) return;
        float normalized = Mth.clamp(ClientHeatState.heat / 100.0F, 0.0F, 1.0F);
        float blend = 0.20F + (1.0F - normalized) * 0.22F;
        event.setRed(Mth.lerp(blend, event.getRed(), 1.0F));
        event.setGreen(Mth.lerp(blend, event.getGreen(), 0.30F));
        event.setBlue(Mth.lerp(blend, event.getBlue(), 0.05F));
    }
}
