package com.fjjy.entity;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.fjjy.config.FlyingChestServerConfig;
import com.fjjy.Util;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WildFlyingChestEntity extends FlyingChestEntity {

    private static final Method CLIENT_BREAK_METHOD = ((Supplier<Method>) () -> {
        // Reflection is used to avoid hard dependency on client-only class, which would cause NoClassDefFoundError on dedicated servers
        final String methodName = "breakEffect";
        final String className = "com.fjjy.entity.FlyingChestEffects";
        try {
            return Class.forName(className)
                .getMethod(methodName, FlyingChestEntity.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            com.fjjy.FlyingChests.LOGGER
                .error("[flying-chests] {}.{} not found — client break effect will be skipped. stale method name?", className, methodName, e);
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }).get();

    private static final EntityDataAccessor<Byte> BREAK_PROGRESS =
        SynchedEntityData.defineId(WildFlyingChestEntity.class, EntityDataSerializers.BYTE);

    public static final int TICKS_TO_BREAK = 30;

    // Server-side tracking — not saved, not synced
    private final Map<ServerPlayer, Integer> activeBreakers = new LinkedHashMap<>();

    public WildFlyingChestEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BREAK_PROGRESS, (byte) 0);
    }

    /** Break stage 0 = not breaking, 1-10 = crack stages. Synced to all clients for crumble overlay. */
    public byte getBreakProgress() {
        return entityData.get(BREAK_PROGRESS);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FleeFlyingPathNavigation nav = new FleeFlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setRequiredPathLength(48.0F);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FleeGoal());
        //debug goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, 1.0));
    }

    private class FleeGoal extends Goal {
        private double fleeThreshold = 100;
        private Vec3 weightedAvgThreatPos = Vec3.ZERO;
        private ThreatComponent shortTermThreat = new ThreatComponent(0, 2, .87, 0);
        private ThreatComponent longTermThreat = new ThreatComponent(0, 1, .97, 0);

        private static class ThreatComponent {
            private final double softCap;
            private final double inputScale;
            private final double constantDecay;
            private final double decayFactor;
            private double value = 0;

            ThreatComponent(double softCap, double inputScale, double decayFactor, double constantDecay) {
                this.softCap = softCap;
                this.inputScale = inputScale;
                this.decayFactor = decayFactor;
                this.constantDecay = constantDecay;
            }
            
            /**
             * Gets the current value.
             * The softcap does not apply unless {@link #decay()} is called after {@link #add(double)}
             * and before this method.
             */
            double get() {
                return value;
            }

            void add(double amount) {
                value += amount * inputScale;
            }

            void decay() {
                // scale down excess above soft cap
                double excess = Math.max(0, value - softCap);
                value = excess * decayFactor + (value - excess);

                // constant decay
                value -= constantDecay;
                value = Math.max(value, 0);
            }
        }

        FleeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // numerator for weighted avg threat position
            Vec3 threatPosSum = Vec3.ZERO;
            // denominator for the weighted avg threat position
            double threatPosWeightsSum = 0;

            AABB searchBox = AABB.ofSize(position(), 1, 1, 1).inflate(16.0D);
            for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, searchBox)) {
                double proximity = 100.0 / Math.max(2, distanceToSqr(player));
                double velocity = Util.fastApproxSqrt(player.getDeltaMovement().lengthSqr());
                    
                shortTermThreat.add(proximity);
                longTermThreat.add(velocity * Util.fastApproxSqrt(proximity));

                double weight = proximity;
                threatPosSum = threatPosSum.add(player.getEyePosition().scale(weight));
                threatPosWeightsSum += weight;
            }

            shortTermThreat.decay();
            longTermThreat.decay();

            if (threatPosWeightsSum == 0) {
                // no players, no threat
                return false;
            } else {
                weightedAvgThreatPos = threatPosSum.scale(1 / threatPosWeightsSum);
                return shortTermThreat.get() *  longTermThreat.get() > fleeThreshold;
            }
        }

        @Override
        public void tick() {
            if (navigation.isDone()) {
                ((FleeFlyingPathNavigation) navigation).moveFrom(weightedAvgThreatPos, 16f, 2.8f);
            }
        }
    }

    public void onBreakStart(ServerPlayer player) {
        if (level().isClientSide()) return;
        if (player.getAbilities().instabuild) {
            performBreak((ServerLevel) level());
            return;
        }
        activeBreakers.put(player, 0);
    }

    public void onBreakStop(ServerPlayer player) {
        if (level().isClientSide()) return;
        activeBreakers.remove(player);
        syncBreakStage();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        if (activeBreakers.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level();
        int maxTicks = 0;

        Iterator<Map.Entry<ServerPlayer, Integer>> it = activeBreakers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerPlayer, Integer> entry = it.next();
            ServerPlayer player = entry.getKey();
            // Remove if disconnected or out of range
            double serverRange = FlyingChestServerConfig.INSTANCE.wildChestAttackRange + 2.0;
            if (player.isRemoved() || !player.isAlive()
                    || distanceToSqr(player) > serverRange * serverRange) {
                it.remove();
                continue;
            }
            int ticks = entry.getValue() + 1;
            entry.setValue(ticks);
            if (ticks >= TICKS_TO_BREAK) {
                performBreak(serverLevel);
                return;
            }
            if (ticks > maxTicks) maxTicks = ticks;
        }

        syncBreakStage();
    }

    private void syncBreakStage() {
        int maxTicks = 0;
        for (int t : activeBreakers.values()) if (t > maxTicks) maxTicks = t;
        byte stage = maxTicks == 0 ? 0 : (byte) Math.min(10, (int) ((float) maxTicks / TICKS_TO_BREAK * 10) + 1);
        entityData.set(BREAK_PROGRESS, stage);
    }

    @Override
    public void onClientRemoval() {
        try {
            CLIENT_BREAK_METHOD.invoke(null, this);
        } catch (Throwable ignored) {}
    }

    @Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		if (damageSource.is(DamageTypes.GENERIC_KILL)) {
            // comes from things like /kill so we want to respect it
			performBreak(level);
			return true;
		} else {
            // any normal damage should be ignored
			return false;
		}
	}
}