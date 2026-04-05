package com.fjjy.entity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fjjy.config.FlyingChestServerConfig;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;

public class WildFlyingChestEntity extends FlyingChestEntity {

    private static final EntityDataAccessor<Byte> BREAK_PROGRESS =
        SynchedEntityData.defineId(WildFlyingChestEntity.class, EntityDataSerializers.BYTE);

    public static final int TICKS_TO_BREAK = 30;

    // Server-side tracking — not saved, not synced
    // active: players currently holding attack on this entity this tick
    // paused: players who looked away but haven't released attack
    private final Map<ServerPlayer, Integer> activeBreakers = new LinkedHashMap<>();
    private final Map<ServerPlayer, Integer> pausedBreakers = new LinkedHashMap<>();

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

    public void onBreakStartOrResume(ServerPlayer player) {
        if (this.level().isClientSide()) return;
        if (player.getAbilities().instabuild) {
            performBreak((ServerLevel) this.level());
            return;
        }
        // Move from paused → active (resuming), or add fresh with 0 ticks
        Integer paused = pausedBreakers.remove(player);
        activeBreakers.put(player, paused != null ? paused : 0);
    }

    public void onBreakPause(ServerPlayer player) {
        if (this.level().isClientSide()) return;
        Integer ticks = activeBreakers.remove(player);
        if (ticks != null) pausedBreakers.put(player, ticks);
    }

    public void onBreakStop(ServerPlayer player) {
        if (this.level().isClientSide()) return;
        activeBreakers.remove(player);
        pausedBreakers.remove(player);
        syncBreakStage();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        // Clean up disconnected paused breakers — they'll never resume
        boolean pausedChanged = pausedBreakers.entrySet().removeIf(
            e -> e.getKey().isRemoved() || !e.getKey().isAlive());

        if (activeBreakers.isEmpty()) {
            if (pausedChanged) syncBreakStage();
            return;
        }

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
                pausedBreakers.remove(player);
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
        for (int t : pausedBreakers.values()) if (t > maxTicks) maxTicks = t;
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
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, this.blockPosition(),
            Block.BLOCK_STATE_REGISTRY.getId(Blocks.CHEST.defaultBlockState()));
        this.discard();
    }
}