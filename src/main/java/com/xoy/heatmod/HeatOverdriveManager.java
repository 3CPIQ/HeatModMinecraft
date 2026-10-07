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
        worldTick++;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) tickPlayer(player);
    }

    private static void tickPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        float heat = HEAT.getOrDefault(id, 100.0F);

        boolean cooling = player.isInWaterOrBubble() || level.isRainingAt(player.blockPosition());
        heat = cooling ? Math.min(100.0F, heat + 0.9F) : Math.max(0.0F, heat - 0.15F);
        HEAT.put(id, heat);

        if ((worldTick & 3) == 0) sync(player, heat);
        if (heat <= 0.0F && player.tickCount % 10 == 0) player.hurt(level.damageSources().generic(), 1.0F);

        RandomSource random = level.random;
        if (worldTick % 10 == 0) {
            meltNearby(level, player);
            convertWaterNearby(level, player);
            igniteNearby(level, player, 42, 60);
        }

        if (worldTick % 20 == 0 && random.nextInt(4) == 0) createRupture(level, player);
    }

    private static void sync(ServerPlayer player, float heat) {
        HeatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HeatSyncPacket(active, heat));
    }

    private static void meltNearby(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < 48; i++) {
            BlockPos pos = origin.offset(random.nextInt(57) - 28, random.nextInt(25) - 12, random.nextInt(57) - 28);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 5, .3, .12, .3, .02);
            } else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.POWDER_SNOW)) {
                level.setBlock(pos, level.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 10, .4, .3, .4, .04);
            }
        }
    }

    private static void createRupture(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        RuptureTarget target = findRuptureTarget(level, player, random);
        if (target == null) return;

        int radius;
        float roll = random.nextFloat();
        if (roll < 0.12F) radius = 9;
        else if (roll < 0.34F) radius = 8;
        else if (roll < 0.62F) radius = 7;
        else if (roll < 0.86F) radius = 6;
        else radius = 5;

        BlockPos origin = target.pos();
        Direction outward = target.outward();
        double x = origin.getX() + .5 + outward.getStepX() * .65;
        double y = origin.getY() + .5 + outward.getStepY() * .65;
        double z = origin.getZ() + .5 + outward.getStepZ() * .65;

        level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 5.0F, 0.52F + random.nextFloat() * .22F);
        level.playSound(null, origin, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 3.3F, 0.48F + random.nextFloat() * .25F);
        level.playSound(null, origin, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.5F, 0.55F + random.nextFloat() * .25F);

        spawnRuptureVfx(level, x, y, z, outward, radius);
        devastateTerrain(level, target, radius, random);
        seedRuptureLava(level, origin, radius, random);
        igniteAround(level, origin, radius + 9, random);
        splashLavaAround(level, player, origin, radius, random);

        for (ServerPlayer nearby : level.players()) {
            double dist = nearby.distanceToSqr(x, y, z);
            if (dist <= 150.0 * 150.0) {
                float strength = Mth.clamp(1.0F - (float)Math.sqrt(dist) / 150.0F, 0.10F, 1.0F);
                HeatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> nearby), new ShakePacket(16 + (int)(strength * 22), strength * 4.4F));
            }
        }
    }

    private static void spawnRuptureVfx(ServerLevel level, double x, double y, double z, Direction outward, int radius) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 18, 2.8, 2.4, 2.8, .18);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 120, 3.8, 3.4, 3.8, .16);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 70, 3.2, 4.5, 3.2, .12);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 170, 3.4, 4.6, 3.4, .35);
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 95, 3.2, 4.8, 3.2, .38);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 100, 5.0, 5.0, 5.0, .08);

        for (int i = 1; i <= 7; i++) {
            double bx = x + outward.getStepX() * i * 1.15;
            double by = y + outward.getStepY() * i * 1.15;
            double bz = z + outward.getStepZ() * i * 1.15;
            double spread = .25 + i * .23;
            level.sendParticles(ParticleTypes.FLAME, bx, by, bz, 22, spread, spread, spread, .24);
            level.sendParticles(ParticleTypes.LAVA, bx, by, bz, 10, spread, spread, spread, .28);
            if ((i & 1) == 0) level.sendParticles(ParticleTypes.EXPLOSION, bx, by, bz, 2, spread, spread, spread, .04);
        }
    }

    private static RuptureTarget findRuptureTarget(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos base = player.blockPosition();
        Direction[] dirs = Direction.values();

        for (int attempt = 0; attempt < 96; attempt++) {
            BlockPos pos = base.offset(random.nextInt(97) - 48, random.nextInt(49) - 24, random.nextInt(97) - 48);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isSolidRuptureFace(level, pos, state)) continue;

            int start = random.nextInt(dirs.length);
            for (int i = 0; i < dirs.length; i++) {
                Direction dir = dirs[(start + i) % dirs.length];
                BlockPos outside = pos.relative(dir);
                BlockState outsideState = level.getBlockState(outside);
                if ((outsideState.isAir() || outsideState.canBeReplaced()) && outsideState.getFluidState().isEmpty()) {
                    return new RuptureTarget(pos, dir);
                }
            }
        }

        for (int attempt = 0; attempt < 24; attempt++) {
            int x = base.getX() + random.nextInt(89) - 44;
            int z = base.getZ() + random.nextInt(89) - 44;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos pos = new BlockPos(x, y, z);
            if (level.hasChunkAt(pos) && isSolidRuptureFace(level, pos, level.getBlockState(pos))) return new RuptureTarget(pos, Direction.UP);
        }
        return null;
    }

    private static boolean isSolidRuptureFace(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        if (!state.canOcclude()) return false;
        if (state.is(BlockTags.LEAVES)) return false;
        if (level.getBlockEntity(pos) != null) return false;
        return !state.is(Blocks.GLASS)
                && !state.is(Blocks.GLASS_PANE)
                && !state.is(Blocks.IRON_BARS)
                && !state.is(Blocks.BARRIER)
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.END_PORTAL_FRAME);
    }

    private static void devastateTerrain(ServerLevel level, RuptureTarget target, int radius, RandomSource random) {
        Direction inward = target.outward().getOpposite();
        BlockPos mainCenter = target.pos().relative(inward, Math.max(1, radius / 3));
        carveIrregularSphere(level, mainCenter, radius, random);

        int gouges = 4 + random.nextInt(4);
        for (int i = 0; i < gouges; i++) {
            int sideX = random.nextInt(radius * 2 + 1) - radius;
            int sideY = random.nextInt(radius * 2 + 1) - radius;
            int sideZ = random.nextInt(radius * 2 + 1) - radius;
            BlockPos lobe = mainCenter.offset(sideX, sideY, sideZ).relative(inward, random.nextInt(Math.max(2, radius / 2 + 1)));
            carveIrregularSphere(level, lobe, 2 + random.nextInt(4), random);
        }

        for (int i = 0; i < radius * 3; i++) {
            BlockPos crack = target.pos().offset(random.nextInt(radius * 3 + 1) - radius, random.nextInt(radius * 2 + 1) - radius, random.nextInt(radius * 3 + 1) - radius);
            if (!level.hasChunkAt(crack)) continue;
            for (int d = 0; d < 2 + random.nextInt(5); d++) {
                BlockPos p = crack.relative(inward, d);
                BlockState state = level.getBlockState(p);
                if (canRupture(level, p, state)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void carveIrregularSphere(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        double rr = radius + .35;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > rr + random.nextDouble() * 1.15 - .55) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (canRupture(level, pos, state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void seedRuptureLava(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        int sources = 12 + radius * 4;
        for (int i = 0; i < sources; i++) {
            BlockPos candidate = center.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(radius * 2 + 1) - radius, random.nextInt(radius * 2 + 1) - radius);
            if (!level.hasChunkAt(candidate)) continue;
            BlockState state = level.getBlockState(candidate);
            if (!state.isAir() && !state.canBeReplaced()) continue;

            boolean touchingSolid = false;
            for (Direction dir : Direction.values()) {
                BlockState neighbor = level.getBlockState(candidate.relative(dir));
                if (!neighbor.isAir() && neighbor.getFluidState().isEmpty()) {
                    touchingSolid = true;
                    break;
                }
            }
            if (touchingSolid) {
                level.setBlock(candidate, Blocks.LAVA.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.LAVA, candidate.getX() + .5, candidate.getY() + .5, candidate.getZ() + .5, 12, .8, 1.4, .8, .24);
            }
        }
    }

    private static void splashLavaAround(ServerLevel level, ServerPlayer player, BlockPos origin, int radius, RandomSource random) {
        if (level.dimensionType().hasCeiling()) return;
        int splashes = 5 + random.nextInt(5);
        for (int i = 0; i < splashes; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = radius + 4 + random.nextInt(radius + 10);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos landing = new BlockPos(x, y, z);
            if (!level.hasChunkAt(landing)) continue;
            BlockState here = level.getBlockState(landing);
            if ((here.isAir() || here.canBeReplaced()) && level.getBlockEntity(landing.below()) == null) {
                level.setBlock(landing, Blocks.LAVA.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.LAVA, landing.getX() + .5, landing.getY() + 1.0, landing.getZ() + .5, 20, 1.2, 2.5, 1.2, .3);
                level.sendParticles(ParticleTypes.FLAME, landing.getX() + .5, landing.getY() + 1.0, landing.getZ() + .5, 25, 1.3, 1.5, 1.3, .2);
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
        BlockPos base = player.blockPosition();
        int changedBlobs = 0;

        for (int column = 0; column < 36 && changedBlobs < 3; column++) {
            int x = base.getX() + random.nextInt(65) - 32;
            int z = base.getZ() + random.nextInt(65) - 32;
            int top = Math.min(level.getMaxBuildHeight() - 2, Math.max(base.getY() + 18, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2));
            int bottom = Math.max(level.getMinBuildHeight() + 1, base.getY() - 28);

            for (int y = top; y >= bottom; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.hasChunkAt(pos)) break;
                if (!level.getFluidState(pos).is(Fluids.WATER)) continue;
                int radius = 3 + random.nextInt(3);
                if (convertWaterBlob(level, pos, radius)) changedBlobs++;
                break;
            }
        }
    }

    private static boolean convertWaterBlob(ServerLevel level, BlockPos center, int radius) {
        boolean changed = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(p)) continue;
                    if (level.getFluidState(p).is(Fluids.WATER)) {
                        level.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            level.sendParticles(ParticleTypes.CLOUD, center.getX() + .5, center.getY() + 1.0, center.getZ() + .5, 70, 3.5, 1.8, 3.5, .12);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.getX() + .5, center.getY() + 1.0, center.getZ() + .5, 40, 3.0, 1.5, 3.0, .08);
            level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2.0F, .58F);
        }
        return changed;
    }

    private static void igniteNearby(ServerLevel level, ServerPlayer player, int radius, int attempts) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < attempts; i++) {
            BlockPos pos = origin.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(27) - 13, random.nextInt(radius * 2 + 1) - radius);
            tryIgnite(level, pos, random);
        }
    }

    private static void igniteAround(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        for (int i = 0; i < 130; i++) {
            BlockPos pos = center.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(19) - 7, random.nextInt(radius * 2 + 1) - radius);
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
                || state.is(BlockTags.WOOL)
                || state.isFlammable(level, pos, Direction.UP);
        if (!flammable) return;

        Direction[] directions = Direction.values();
        for (int tries = 0; tries < 6; tries++) {
            BlockPos firePos = pos.relative(directions[random.nextInt(directions.length)]);
            if (level.getBlockState(firePos).isAir()) {
                level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.FLAME, firePos.getX() + .5, firePos.getY() + .5, firePos.getZ() + .5, 8, .35, .45, .35, .07);
                return;
            }
        }
    }

    private record RuptureTarget(BlockPos pos, Direction outward) {}
}
