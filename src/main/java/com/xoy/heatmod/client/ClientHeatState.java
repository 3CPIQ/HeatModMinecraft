package com.xoy.heatmod.client;

import com.xoy.heatmod.HeatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;

public final class ClientHeatState {
    public static boolean active;
    public static float heat = 100.0F;
    public static int shakeTicks;
    public static float shakeStrength;

    private static SoundInstance music;
    private static int musicCheckTicks;

    private ClientHeatState() {}

    public static void setHeat(boolean enabled, float value) {
        boolean changed = active != enabled;
        active = enabled;
        heat = value;

        if (changed) {
            if (enabled) startMusic();
            else stopMusic();
        }
    }

    public static void shake(int ticks, float strength) {
        shakeTicks = Math.max(shakeTicks, ticks);
        shakeStrength = Math.max(shakeStrength, strength);
    }

    public static void tick() {
        if (shakeTicks > 0) {
            shakeTicks--;
            shakeStrength *= 0.90F;
        } else {
            shakeStrength = 0.0F;
        }

        if (active) {
            musicCheckTicks++;
            if (musicCheckTicks >= 100) {
                musicCheckTicks = 0;
                Minecraft mc = Minecraft.getInstance();
                if (music == null || !mc.getSoundManager().isActive(music)) startMusic();
            }
        }
    }

    private static void startMusic() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (music != null) mc.getSoundManager().stop(music);
        music = SimpleSoundInstance.forMusic(HeatMod.DEAD_HEAT_PULSE.get());
        mc.getSoundManager().play(music);
        musicCheckTicks = 0;
    }

    private static void stopMusic() {
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
            music = null;
        }
        musicCheckTicks = 0;
    }
}
