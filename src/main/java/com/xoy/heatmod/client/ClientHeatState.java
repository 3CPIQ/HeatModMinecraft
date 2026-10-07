package com.xoy.heatmod.client;

public final class ClientHeatState {
    public static boolean active;
    public static float heat = 100.0F;
    public static int shakeTicks;
    public static float shakeStrength;

    private ClientHeatState() {}

    public static void setHeat(boolean enabled, float value) {
        active = enabled;
        heat = value;
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
    }
}
