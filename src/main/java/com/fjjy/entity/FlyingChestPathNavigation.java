package com.fjjy.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

import com.fjjy.entity.FlyingChestNodeEvaluator;

public class FlyingChestPathNavigation extends FlyingPathNavigation {
    private FlyingChestPathFinder fleePathFinder;

    public FlyingChestPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        FlyingChestNodeEvaluator evaluator = new FlyingChestNodeEvaluator();
        this.nodeEvaluator = evaluator;
        this.fleePathFinder = new FlyingChestPathFinder(evaluator, maxVisitedNodes);
        return this.fleePathFinder;
    }

    private void startFleeing(Vec3 threatPos, float goalDistance) {
        fleePathFinder.startFleeing(threatPos, goalDistance);
    }

    private void stopFleeing() {
        fleePathFinder.stopFleeing();
    }


    public Path createFleePath(Vec3 threatPos, float goalDistance) {
        try {
            startFleeing(threatPos, goalDistance);
            return createPath(BlockPos.containing(threatPos), 0);
        } finally {
            stopFleeing();
        }
    }

    public boolean moveFrom(Vec3 threat, float goalDistance, double speed) {
        return moveTo(createFleePath(threat, goalDistance), speed);
    }
}
