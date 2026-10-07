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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
            igniteNearby(level, player, 30, 54);
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
            BlockPos pos = origin.offset(random.nextInt(49) - 24, random.nextInt(21) - 10, random.nextInt(49) - 24);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.POWDER_SNOW)) {
                level.setBlock(pos, level.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 10, .4, .3, .4, .04);
            }
        }
    }

    private static void createRupture(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        RuptureTarget target = findNearbyRuptureTarget(level, player, random);
        if (target == null) return;

        int radius;
        float roll = random.nextFloat();
        if (roll < .05F) radius = 6;
        else if (roll < .28F) radius = 5;
        else if (roll < .68F) radius = 4;
        else radius = 3;

        BlockPos origin = target.pos();
        Direction outward = target.outward();
        double x = origin.getX() + .5 + outward.getStepX() * .7;
        double y = origin.getY() + .5 + outward.getStepY() * .7;
        double z = origin.getZ() + .5 + outward.getStepZ() * .7;

        level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 5.0F, .50F + random.nextFloat() * .2F);
        level.playSound(null, origin, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 3.4F, .50F + random.nextFloat() * .2F);
        level.playSound(null, origin, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.7F, .55F + random.nextFloat() * .2F);

        spawnRuptureVfx(level, x, y, z, outward);
        devastateTerrain(level, target, radius, random);
        seedRuptureLava(level, origin, radius, random);
        seedLavaCore(level, target, radius, random);
        igniteAround(level, origin, radius + 8, random);
        splashLavaAround(level, origin, radius, random);

        for (ServerPlayer nearby : level.players()) {
            double dist = nearby.distanceToSqr(x, y, z);
            if (dist <= 100.0 * 100.0) {
                float strength = Mth.clamp(1.0F - (float)Math.sqrt(dist) / 100.0F, .15F, 1.0F);
                HeatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> nearby), new ShakePacket(18 + (int)(strength * 24), strength * 4.7F));
            }
        }
    }

    private static RuptureTarget findNearbyRuptureTarget(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos base = player.blockPosition();
        List<RuptureTarget> veryClose = new ArrayList<>();
        List<RuptureTarget> close = new ArrayList<>();

        int[] radii = {2, 3, 4, 5, 6, 7, 8, 10, 12};
        for (int radius : radii) {
            scanShell(level, base, radius, veryClose, close);
            if (radius <= 6 && !veryClose.isEmpty()) break;
        }

        if (!veryClose.isEmpty()) {
            Collections.shuffle(veryClose);
            return veryClose.get(random.nextInt(veryClose.size()));
        }
        if (!close.isEmpty()) return close.get(random.nextInt(close.size()));

        for (int attempt = 0; attempt < 48; attempt++) {
            int dx = random.nextInt(25) - 12;
            int dy = random.nextInt(15) - 7;
            int dz = random.nextInt(25) - 12;
            if (dx * dx + dy * dy + dz * dz < 9) continue;
            RuptureTarget t = evaluateSurface(level, base.offset(dx, dy, dz), base);
            if (t != null) return t;
        }
        return null;
    }

    private static void scanShell(ServerLevel level, BlockPos base, int r, List<RuptureTarget> veryClose, List<RuptureTarget> close) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -Math.min(r, 5); dy <= Math.min(r, 5); dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int max = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
                    if (max != r) continue;
                    RuptureTarget t = evaluateSurface(level, base.offset(dx, dy, dz), base);
                    if (t == null) continue;
                    if (r <= 6) veryClose.add(t); else close.add(t);
                }
            }
        }
    }

    private static RuptureTarget evaluateSurface(ServerLevel level, BlockPos pos, BlockPos playerPos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!isSolidRuptureFace(level, pos, state)) return null;

        Direction best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            BlockPos outside = pos.relative(dir);
            BlockState outsideState = level.getBlockState(outside);
            if (!(outsideState.isAir() || outsideState.canBeReplaced()) || !outsideState.getFluidState().isEmpty()) continue;
            double distance = outside.distSqr(playerPos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = dir;
            }
        }
        return best == null ? null : new RuptureTarget(pos, best);
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
                && !state.is(Blocks.END_PORTAL_FRAME)
                && !state.is(Blocks.NETHER_PORTAL)
                && !state.is(Blocks.END_PORTAL);
    }

    private static void spawnRuptureVfx(ServerLevel level, double x, double y, double z, Direction outward) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 22, 2.4, 2.4, 2.4, .2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 145, 3.5, 3.5, 3.5, .17);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 210, 3.3, 4.0, 3.3, .38);
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 150, 3.4, 4.4, 3.4, .44);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 120, 4.5, 4.5, 4.5, .10);
        for (int i = 1; i <= 8; i++) {
            double bx = x + outward.getStepX() * i * 1.05;
            double by = y + outward.getStepY() * i * 1.05;
            double bz = z + outward.getStepZ() * i * 1.05;
            double spread = .2 + i * .2;
            level.sendParticles(ParticleTypes.FLAME, bx, by, bz, 24, spread, spread, spread, .27);
            level.sendParticles(ParticleTypes.LAVA, bx, by, bz, 18, spread, spread, spread, .34);
            if ((i & 1) == 0) level.sendParticles(ParticleTypes.EXPLOSION, bx, by, bz, 2, spread, spread, spread, .05);
        }
    }

    private static void devastateTerrain(ServerLevel level, RuptureTarget target, int radius, RandomSource random) {
        Direction inward = target.outward().getOpposite();
        BlockPos mainCenter = target.pos().relative(inward, 1);
        carveIrregularSphere(level, mainCenter, radius, random);

        int gouges = 1 + random.nextInt(3);
        for (int i = 0; i < gouges; i++) {
            int spread = Math.max(2, radius - 1);
            BlockPos lobe = mainCenter.offset(
                    random.nextInt(spread * 2 + 1) - spread,
                    random.nextInt(spread * 2 + 1) - spread,
                    random.nextInt(spread * 2 + 1) - spread
            ).relative(inward, random.nextInt(2));
            carveIrregularSphere(level, lobe, 1 + random.nextInt(2), random);
        }
    }

    private static void carveIrregularSphere(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        double rr = radius + .20;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > rr + random.nextDouble() * .70 - .35) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (canRupture(level, pos, state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void seedRuptureLava(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        int attempts = 30 + radius * 10;
        for (int i = 0; i < attempts; i++) {
            int spread = radius + 2;
            BlockPos p = center.offset(
                    random.nextInt(spread * 2 + 1) - spread,
                    random.nextInt(spread * 2 + 1) - spread,
                    random.nextInt(spread * 2 + 1) - spread
            );
            if (!level.hasChunkAt(p)) continue;
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && !state.canBeReplaced()) continue;

            int solidFaces = 0;
            for (Direction dir : Direction.values()) {
                BlockState neighbor = level.getBlockState(p.relative(dir));
                if (!neighbor.isAir() && neighbor.getFluidState().isEmpty()) solidFaces++;
            }
            if (solidFaces > 0 && random.nextFloat() < .78F) {
                level.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.LAVA, p.getX() + .5, p.getY() + .5, p.getZ() + .5, 8, .5, .8, .5, .18);
            }
        }
    }

    private static void seedLavaCore(ServerLevel level, RuptureTarget target, int radius, RandomSource random) {
        Direction inward = target.outward().getOpposite();
        BlockPos core = target.pos().relative(inward, Math.max(1, radius / 2));
        int coreRadius = Math.max(1, radius - 2);

        for (int dx = -coreRadius; dx <= coreRadius; dx++) {
            for (int dz = -coreRadius; dz <= coreRadius; dz++) {
                if (dx * dx + dz * dz > coreRadius * coreRadius) continue;
                for (int dy = -coreRadius; dy <= coreRadius; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if (!level.hasChunkAt(p)) continue;
                    BlockState state = level.getBlockState(p);
                    if (!state.isAir() && !state.canBeReplaced()) continue;

                    BlockState below = level.getBlockState(p.below());
                    if (!below.isAir() && below.getFluidState().isEmpty() && random.nextFloat() < .68F) {
                        level.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
                        break;
                    }
                }
            }
        }
    }

    private static void splashLavaAround(ServerLevel level, BlockPos origin, int radius, RandomSource random) {
        if (level.dimensionType().hasCeiling()) return;
        for (int i = 0; i < 10 + random.nextInt(8); i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = radius + 2 + random.nextInt(radius + 10);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos landing = new BlockPos(x, y, z);
            if (!level.hasChunkAt(landing)) continue;
            BlockState here = level.getBlockState(landing);
            if ((here.isAir() || here.canBeReplaced()) && level.getBlockEntity(landing.below()) == null) {
                level.setBlock(landing, Blocks.LAVA.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.LAVA, landing.getX() + .5, landing.getY() + 1.0, landing.getZ() + .5, 28, 1.4, 2.8, 1.4, .34);
                level.sendParticles(ParticleTypes.FLAME, landing.getX() + .5, landing.getY() + 1.0, landing.getZ() + .5, 30, 1.4, 1.8, 1.4, .22);
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
        int changed = 0;
        for (int attempt = 0; attempt < 60 && changed < 5; attempt++) {
            BlockPos p = base.offset(random.nextInt(49) - 24, random.nextInt(19) - 9, random.nextInt(49) - 24);
            if (!level.hasChunkAt(p) || !level.getFluidState(p).is(Fluids.WATER)) continue;
            int radius = 4 + random.nextInt(3);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos q = p.offset(dx, dy, dz);
                        if (level.hasChunkAt(q) && level.getFluidState(q).is(Fluids.WATER)) level.setBlock(q, Blocks.LAVA.defaultBlockState(), 3);
                    }
                }
            }
            level.sendParticles(ParticleTypes.CLOUD, p.getX() + .5, p.getY() + 1.0, p.getZ() + .5, 80, 3.6, 1.8, 3.6, .12);
            level.sendParticles(ParticleTypes.LAVA, p.getX() + .5, p.getY() + 1.0, p.getZ() + .5, 24, 2.4, 1.6, 2.4, .20);
            changed++;
        }
    }

    private static void igniteNearby(ServerLevel level, ServerPlayer player, int radius, int attempts) {
        RandomSource random = level.random;
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < attempts; i++) {
            BlockPos pos = origin.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(23) - 11, random.nextInt(radius * 2 + 1) - radius);
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
        boolean flammable = state.is(BlockTags.LOGS) || state.is(BlockTags.LOGS_THAT_BURN) || state.is(BlockTags.LEAVES) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL) || state.isFlammable(level, pos, Direction.UP);
        if (!flammable) return;
        Direction[] directions = Direction.values();
        for (int tries = 0; tries < 6; tries++) {
            BlockPos firePos = pos.relative(directions[random.nextInt(directions.length)]);
            if (level.getBlockState(firePos).isAir()) {
                level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                return;
            }
        }
    }

    private record RuptureTarget(BlockPos pos, Direction outward) {}
}
