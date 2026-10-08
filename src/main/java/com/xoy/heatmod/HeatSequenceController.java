package com.xoy.heatmod;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeatMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HeatSequenceController {
    private static final int ACTIVATION_DELAY_TICKS = 62;

    private static boolean pendingActivation;
    private static boolean restoreHeatResistance;
    private static int ticks;

    private HeatSequenceController() {}

    public static void start(MinecraftServer server) {
        restoreHeatResistance = HeatOverdriveManager.doHeatResistence();
        if (restoreHeatResistance) {
            HeatOverdriveManager.setDoHeatResistence(server, false);
        }

        HeatOverdriveManager.start(server);
        pendingActivation = true;
        ticks = 0;
    }

    public static void stop(MinecraftServer server) {
        pendingActivation = false;
        ticks = 0;
        HeatOverdriveManager.stop(server);

        if (restoreHeatResistance) {
            HeatOverdriveManager.setDoHeatResistence(server, true);
        }
        restoreHeatResistance = false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pendingActivation) return;

        ticks++;
        if (ticks < ACTIVATION_DELAY_TICKS) return;

        pendingActivation = false;
        ticks = 0;

        if (restoreHeatResistance && HeatOverdriveManager.isActive()) {
            HeatOverdriveManager.setDoHeatResistence(event.getServer(), true);
        }
        restoreHeatResistance = false;
    }
}
