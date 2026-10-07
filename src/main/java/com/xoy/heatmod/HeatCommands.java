package com.xoy.heatmod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HeatMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HeatCommands {
    private HeatCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("heat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("overdrive")
                        .then(Commands.literal("start").executes(ctx -> {
                            HeatOverdriveManager.start(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Heat Overdrive started."), true);
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            HeatOverdriveManager.stop(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Heat Overdrive stopped."), true);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Heat Overdrive: " + (HeatOverdriveManager.isActive() ? "ACTIVE" : "OFF")), false);
                            return 1;
                        }))));
    }
}
