package com.fjjy.entity;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fjjy.config.FlyingChestServerConfig;

import java.util.function.Supplier;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class WildFlyingChestEntity extends FlyingChestEntity {

    private static final Method CLIENT_BREAK_METHOD = ((Supplier<Method>) () -> {
        // Reflection is used to avoid hard dependency on client-only class, which would cause NoClassDefFoundError on dedicated servers
        try {
            return Class.forName("com.fjjy.entity.WildFlyingChestClientEvents")
                .getMethod("onBreak", WildFlyingChestEntity.class);
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
        return this.entityData.get(BREAK_PROGRESS);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new WaterAvoidingRandomFlyingGoal(this, 1.0));
    }

    public void onBreakStart(ServerPlayer player) {
        if (this.level().isClientSide()) return;
        if (player.getAbilities().instabuild) {
            performBreak((ServerLevel) this.level());
            return;
        }
        activeBreakers.put(player, 0);
    }

    public void onBreakStop(ServerPlayer player) {
        if (this.level().isClientSide()) return;
        activeBreakers.remove(player);
        syncBreakStage();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        if (activeBreakers.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) this.level();
        int maxTicks = 0;

        Iterator<Map.Entry<ServerPlayer, Integer>> it = activeBreakers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerPlayer, Integer> entry = it.next();
            ServerPlayer player = entry.getKey();
            // Remove if disconnected or out of range
            double serverRange = FlyingChestServerConfig.INSTANCE.wildChestAttackRange + 2.0;
            if (player.isRemoved() || !player.isAlive()
                    || this.distanceToSqr(player) > serverRange * serverRange) {
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
        this.entityData.set(BREAK_PROGRESS, stage);
    }

    void performBreak(ServerLevel level) {
        for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, this.blockPosition(), stack);
            }
        }
        this.discard();
    }

    @Override
    public void onClientRemoval() {
        try {
            CLIENT_BREAK_METHOD.invoke(null, this);
        } catch (Throwable ignored) {}
    }
}