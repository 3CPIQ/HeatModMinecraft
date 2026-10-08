package com.xoy.heatmod.client;

public final class ClientHeatState {
    public static boolean active;
    public static float heat = 100.0F;
    public static int shakeTicks;
    public static float shakeStrength;

    public static float orangeFade;
    public static float hudFade;
    public static int sequenceTicks;
    public static boolean starting;
    public static boolean stopping;
    public static boolean activationBoomPending;
    public static boolean musicReady;

    private static final int ORANGE_FADE_TICKS = 48;
    private static final int HUD_FADE_TICKS = 14;

    private ClientHeatState() {}

    public static void setHeat(boolean enabled, float value) {
        boolean changed = active != enabled;
        heat = value;

        if (changed) {
            if (enabled) beginStart();
            else beginStop();
        }

        active = enabled;
    }

    private static void beginStart() {
        starting = true;
        stopping = false;
        sequenceTicks = 0;
        orangeFade = 0.0F;
        hudFade = 0.0F;
        activationBoomPending = false;
        musicReady = false;
    }

    private static void beginStop() {
        starting = false;
        stopping = true;
        sequenceTicks = 0;
        activationBoomPending = false;
        musicReady = false;
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

        if (starting) {
            sequenceTicks++;

            if (sequenceTicks <= ORANGE_FADE_TICKS) {
                orangeFade = clamp01(sequenceTicks / (float) ORANGE_FADE_TICKS);
                hudFade = 0.0F;
                return;
            }

            if (sequenceTicks == ORANGE_FADE_TICKS + 1) {
                activationBoomPending = true;
                musicReady = true;
            }

            int hudTicks = sequenceTicks - ORANGE_FADE_TICKS;
            hudFade = clamp01(hudTicks / (float) HUD_FADE_TICKS);
            orangeFade = 1.0F;

            if (hudFade >= 1.0F) starting = false;
            return;
        }

        if (stopping) {
            sequenceTicks++;

            if (hudFade > 0.0F) {
                hudFade = Math.max(0.0F, hudFade - 0.11F);
                return;
            }

            orangeFade = Math.max(0.0F, orangeFade - 0.035F);
            if (orangeFade <= 0.0F) {
                orangeFade = 0.0F;
                stopping = false;
            }
            return;
        }

        if (active) {
            orangeFade = 1.0F;
            hudFade = 1.0F;
        }
    }

    public static boolean shouldRenderHeatVisuals() {
        return active || starting || stopping || orangeFade > 0.001F || hudFade > 0.001F;
    }

    public static boolean consumeActivationBoom() {
        if (!activationBoomPending) return false;
        activationBoomPending = false;
        return true;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
