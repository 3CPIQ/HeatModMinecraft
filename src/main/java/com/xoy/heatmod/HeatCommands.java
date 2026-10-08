package com.xoy.heatmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
                            HeatSequenceController.start(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Heat Overdrive started."), true);
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            HeatSequenceController.stop(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Heat Overdrive stopped."), true);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Heat Overdrive: " + (HeatOverdriveManager.isActive() ? "ACTIVE" : "OFF")), false);
                            return 1;
                        })))
                .then(Commands.literal("gamerule")
                        .then(Commands.literal("doHeatResistence")
                                .executes(ctx -> {
                                    boolean value = HeatOverdriveManager.doHeatResistence();
                                    ctx.getSource().sendSuccess(() -> Component.literal("doHeatResistence = " + value), false);
                                    return value ? 1 : 0;
                                })
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean value = BoolArgumentType.getBool(ctx, "value");
                                            HeatOverdriveManager.setDoHeatResistence(ctx.getSource().getServer(), value);
                                            ctx.getSource().sendSuccess(() -> Component.literal("doHeatResistence set to " + value), true);
                                            return 1;
                                        })))));
    }
}
