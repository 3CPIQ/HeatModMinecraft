package com.xoy.heatmod;

import com.xoy.heatmod.network.HeatNetwork;
import com.xoy.heatmod.network.HeatSyncPacket;
import com.xoy.heatmod.network.ShakePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HeatMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HeatOverdriveManager {
    private static final Map<UUID, Float> HEAT = new HashMap<>();
    private static boolean active;
    private static int worldTick;

    private HeatOverdriveManager() {}

    public static boolean isActive() {
        return active;
    }

    public static void start(MinecraftServer server) {
        active = true;
        worldTick = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HEAT.put(player.getUUID(), 100.0F);
            sync(player, 100.0F);
        }
    }

    public static void stop(MinecraftServer server) {
        active = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HEAT.put(player.getUUID(), 100.0F);
            sync(player, 100.0F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        MinecraftServer server = event.getServer();
        worldTick++;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        float heat = HEAT.getOrDefault(id, 100.0F);

        boolean cooling = player.isInWaterOrBubble() || level.isRainingAt(player.blockPosition());
        if (cooling) {
            heat = Math.min(100.0F, heat + 0.9F);
        } else {
            heat = Math.max(0.0F, heat - 0.15F);
        }
        HEAT.put(id, heat);

        if ((worldTick & 3) == 0) sync(player, heat);

        if (heat <= 0.0F && player.tickCount % 10 == 0) {
            player.hurt(level.damageSources().generic(), 1.0F);
        }

        if (worldTick % 20 == 0) {
            meltNearby(level, player);
            RandomSource random = level.random;
            if (random.nextInt(9) == 0) {
                createRupture(level, player);
            }
            if (random.nextInt(14) == 0) {
                seedLavaInWater(level, player);
            }
        }
    }

    private static void sync(ServerPlayer player, float heat) {
        HeatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HeatSyncPacket(active, heat));
    }

    private static void meltNearby(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < 18; i++) {
            BlockPos pos = origin.offset(random.nextInt(41) - 20, random.nextInt(17) - 8, random.nextInt(41) - 20);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 4, .25, .1, .25, .01);
            } else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.POWDER_SNOW)) {
                level.setBlock(pos, level.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 7, .3, .2, .3, .02);
            }
        }
    }

    private static void createRupture(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos surface = findSurface(level, player, random);
        if (surface == null) return;

        double x = surface.getX() + .5;
        double y = surface.getY() + .5;
        double z = surface.getZ() + .5;

        level.playSound(null, surface, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.2F, 0.75F + random.nextFloat() * 0.35F);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 2, .45, .25, .45, .02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 24, 1.0, .6, 1.0, .03);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 18, .8, .35, .8, .03);

        int radius = random.nextFloat() < 0.22F ? 2 : 1;
        carveCrater(level, surface, radius);

        BlockPos lavaPos = surface.below();
        if (level.getBlockState(lavaPos).isAir() || level.getBlockState(lavaPos).canBeReplaced()) {
            level.setBlock(lavaPos, Blocks.LAVA.defaultBlockState(), 3);
        } else if (level.getBlockState(surface).isAir()) {
            level.setBlock(surface, Blocks.LAVA.defaultBlockState(), 3);
        }

        for (ServerPlayer nearby : level.players()) {
            double dist = nearby.distanceToSqr(x, y, z);
            if (dist <= 80.0 * 80.0) {
                float strength = Mth.clamp(1.0F - (float)Math.sqrt(dist) / 80.0F, 0.08F, 1.0F);
                HeatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> nearby), new ShakePacket(8 + (int)(strength * 8), strength * 1.6F));
            }
        }
    }

    private static BlockPos findSurface(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos base = player.blockPosition();
        for (int attempt = 0; attempt < 18; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 12 + random.nextInt(37);
            int x = base.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = base.getZ() + Mth.floor(Math.sin(angle) * distance);

            if (level.dimensionType().hasCeiling()) {
                int top = Math.min(level.getMaxBuildHeight() - 2, base.getY() + 18);
                int bottom = Math.max(level.getMinBuildHeight() + 1, base.getY() - 28);
                for (int y = top; y >= bottom; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    BlockState above = level.getBlockState(pos.above());
                    if (!state.isAir() && state.canOcclude() && (above.isAir() || above.canBeReplaced())) return pos;
                }
            } else {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (y > level.getMinBuildHeight()) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (Math.abs(y - base.getY()) <= 64 && !level.getBlockState(pos).isAir()) return pos;
                }
            }
        }
        return null;
    }

    private static void carveCrater(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy >= -radius; dy--) {
                    double d = Math.sqrt(dx * dx + dz * dz + (dy * 0.8) * (dy * 0.8));
                    if (d > radius + 0.35) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (canRupture(level, pos, state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static boolean canRupture(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        if (level.getBlockEntity(pos) != null) return false;
        if (state.is(BlockTags.WITHER_IMMUNE)) return false;
        return !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.BARRIER)
                && !state.is(Blocks.END_PORTAL)
                && !state.is(Blocks.END_PORTAL_FRAME)
                && !state.is(Blocks.NETHER_PORTAL)
                && !state.is(Blocks.COMMAND_BLOCK)
                && !state.is(Blocks.CHAIN_COMMAND_BLOCK)
                && !state.is(Blocks.REPEATING_COMMAND_BLOCK)
                && !state.is(Blocks.STRUCTURE_BLOCK)
                && !state.is(Blocks.JIGSAW);
    }

    private static void seedLavaInWater(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos pos = origin.offset(random.nextInt(33) - 16, random.nextInt(11) - 5, random.nextInt(33) - 16);
            if (!level.hasChunkAt(pos)) continue;
            if (level.getFluidState(pos).is(Fluids.WATER)) {
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + 1.0, pos.getZ() + .5, 16, .8, .35, .8, .04);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2F, 0.7F);
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        BlockPos p = pos.offset(dx, 0, dz);
                        if (level.getFluidState(p).is(Fluids.WATER)) level.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
                    }
                }
                return;
            }
        }
    }
}
