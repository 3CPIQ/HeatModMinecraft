package com.xoy.heatmod;

import com.xoy.heatmod.network.HeatNetwork;
import com.xoy.heatmod.network.HeatSyncPacket;
import com.xoy.heatmod.network.ShakePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

        RandomSource random = level.random;

        if (worldTick % 10 == 0) {
            meltNearby(level, player);
            convertWaterNearby(level, player);
            igniteNearby(level, player, 34, 22);
        }

        if (worldTick % 20 == 0 && random.nextInt(5) == 0) {
            createRupture(level, player);
        }
    }

    private static void sync(ServerPlayer player, float heat) {
        HeatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HeatSyncPacket(active, heat));
    }

    private static void meltNearby(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < 32; i++) {
            BlockPos pos = origin.offset(random.nextInt(49) - 24, random.nextInt(21) - 10, random.nextInt(49) - 24);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 5, .3, .12, .3, .02);
            } else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.POWDER_SNOW)) {
                level.setBlock(pos, level.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 9, .35, .25, .35, .03);
            }
        }
    }

    private static void createRupture(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos surface = findSurface(level, player, random);
        if (surface == null) return;

        double x = surface.getX() + .5;
        double y = surface.getY() + 1.0;
        double z = surface.getZ() + .5;

        level.playSound(null, surface, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 0.62F + random.nextFloat() * 0.23F);
        level.playSound(null, surface, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 2.8F, 0.55F + random.nextFloat() * 0.3F);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 10, 2.2, 1.5, 2.2, .12);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.0, z, 45, 2.0, 2.3, 2.0, .09);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + .5, z, 70, 2.2, 1.5, 2.2, .13);
        level.sendParticles(ParticleTypes.FLAME, x, y + 1.2, z, 105, 2.4, 2.8, 2.4, .24);
        level.sendParticles(ParticleTypes.LAVA, x, y + 1.4, z, 65, 2.1, 2.8, 2.1, .30);
        level.sendParticles(ParticleTypes.ASH, x, y + 2.0, z, 65, 3.2, 3.0, 3.2, .06);

        int radius;
        float roll = random.nextFloat();
        if (roll < 0.12F) radius = 6;
        else if (roll < 0.38F) radius = 5;
        else if (roll < 0.72F) radius = 4;
        else radius = 3;

        carveCrater(level, surface, radius);
        seedCraterLava(level, surface, radius, random);
        igniteAround(level, surface, radius + 5, random);

        for (ServerPlayer nearby : level.players()) {
            double dist = nearby.distanceToSqr(x, y, z);
            if (dist <= 110.0 * 110.0) {
                float strength = Mth.clamp(1.0F - (float)Math.sqrt(dist) / 110.0F, 0.10F, 1.0F);
                HeatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> nearby), new ShakePacket(12 + (int)(strength * 15), strength * 3.0F));
            }
        }
    }

    private static BlockPos findSurface(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos base = player.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 10 + random.nextInt(45);
            int x = base.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = base.getZ() + Mth.floor(Math.sin(angle) * distance);

            if (level.dimensionType().hasCeiling()) {
                int top = Math.min(level.getMaxBuildHeight() - 2, base.getY() + 24);
                int bottom = Math.max(level.getMinBuildHeight() + 1, base.getY() - 36);
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
                    if (Math.abs(y - base.getY()) <= 72 && !level.getBlockState(pos).isAir()) return pos;
                }
            }
        }
        return null;
    }

    private static void carveCrater(ServerLevel level, BlockPos center, int radius) {
        int depth = Math.max(3, radius - 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                if (horizontal > radius + 0.35) continue;

                int localDepth = Math.max(1, (int)Math.round(depth * (1.0 - horizontal / (radius + 0.75))));
                for (int dy = 1; dy >= -localDepth; dy--) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (canRupture(level, pos, state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void seedCraterLava(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        int sources = 5 + radius * 2;
        for (int i = 0; i < sources; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) continue;

            BlockPos probe = center.offset(dx, 1, dz);
            for (int d = 0; d < radius + 6; d++) {
                BlockPos p = probe.below(d);
                if (!level.hasChunkAt(p)) break;
                BlockState state = level.getBlockState(p);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    BlockPos target = p.above();
                    if (level.getBlockState(target).isAir() || level.getBlockState(target).canBeReplaced()) {
                        level.setBlock(target, Blocks.LAVA.defaultBlockState(), 3);
                        level.sendParticles(ParticleTypes.LAVA, target.getX() + .5, target.getY() + .8, target.getZ() + .5, 12, .7, 1.2, .7, .2);
                    }
                    break;
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

    private static void convertWaterNearby(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();

        for (int attempt = 0; attempt < 96; attempt++) {
            BlockPos pos = origin.offset(random.nextInt(49) - 24, random.nextInt(17) - 8, random.nextInt(49) - 24);
            if (!level.hasChunkAt(pos) || !level.getFluidState(pos).is(Fluids.WATER)) continue;

            int radius = 2 + random.nextInt(3);
            boolean changed = false;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = pos.offset(dx, dy, dz);
                        if (!level.hasChunkAt(p)) continue;
                        if (level.getFluidState(p).is(Fluids.WATER)) {
                            level.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + 1.0, pos.getZ() + .5, 42, 2.2, 1.0, 2.2, .09);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + .5, pos.getY() + 1.1, pos.getZ() + .5, 22, 1.7, .9, 1.7, .05);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.6F, 0.65F);
                return;
            }
        }
    }

    private static void igniteNearby(ServerLevel level, ServerPlayer player, int radius, int attempts) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < attempts; i++) {
            BlockPos pos = origin.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(19) - 9, random.nextInt(radius * 2 + 1) - radius);
            tryIgnite(level, pos, random);
        }
    }

    private static void igniteAround(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        for (int i = 0; i < 55; i++) {
            BlockPos pos = center.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(11) - 4, random.nextInt(radius * 2 + 1) - radius);
            tryIgnite(level, pos, random);
        }
    }

    private static void tryIgnite(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        boolean flammable = state.is(BlockTags.LOGS)
                || state.is(BlockTags.LOGS_THAT_BURN)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOOL);
        if (!flammable) return;

        Direction[] directions = Direction.values();
        for (int tries = 0; tries < 4; tries++) {
            BlockPos firePos = pos.relative(directions[random.nextInt(directions.length)]);
            if (level.getBlockState(firePos).isAir()) {
                level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.FLAME, firePos.getX() + .5, firePos.getY() + .5, firePos.getZ() + .5, 8, .25, .35, .25, .05);
                return;
            }
        }
    }
}
