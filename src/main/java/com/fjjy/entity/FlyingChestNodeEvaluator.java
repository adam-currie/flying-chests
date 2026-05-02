package com.fjjy.entity;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.phys.AABB;

public class FlyingChestNodeEvaluator extends NodeEvaluator {
    private final Long2ObjectMap<PathType> pathTypeByPosCache = new Long2ObjectOpenHashMap<>();
    private static final int MAX_START_NODE_CANDIDATES = 10;

    // replaces SMALL_MOB_INFLATED_START_NODE_BOUNDING_BOX = 1.1F; from flyNodeEvaluator
    private static final AABB ZERO_ALIGNED_BOUNDING_BOX = new AABB(-.55, -.55, -.55, .55, .55, .55);

    @Override
    public void prepare(final PathNavigationRegion level, final Mob entity) {
        super.prepare(level, entity);
        entity.onPathfindingStart();
        this.pathTypeByPosCache.clear();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypeByPosCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        int startY;
        if (this.canFloat() && this.mob.isInWater()) {
            startY = this.mob.getBlockY();
            MutableBlockPos reusableBlockPos = new MutableBlockPos(this.mob.getX(), (double) startY, this.mob.getZ());

            for (BlockState state = this.currentContext.getBlockState(reusableBlockPos);
                 state.is(Blocks.WATER);
                 state = this.currentContext.getBlockState(reusableBlockPos)) {
                reusableBlockPos.set(this.mob.getX(), (double) (++startY), this.mob.getZ());
            }
        } else {
            startY = Mth.floor(this.mob.getY() + 0.5);
        }

        BlockPos startPos = BlockPos.containing(this.mob.getX(), startY, this.mob.getZ());
        if (!this.canStartAt(startPos)) {
            for (BlockPos testedPosition : this.iteratePathfindingStartNodeCandidatePositions(this.mob)) {
                if (this.canStartAt(testedPosition)) {
                    return this.getStartNode(testedPosition);
                }
            }
        }

        return this.getStartNode(startPos);
    }

    protected Node getStartNode(final BlockPos pos) {
        Node node = this.getNode(pos);
        node.type = this.getCachedPathType(node.x, node.y, node.z);
        node.costMalus = this.mob.getPathfindingMalus(node.type);
        return node;
    }

    protected boolean canStartAt(final BlockPos pos) {
        PathType blockPathType = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());
        return this.mob.getPathfindingMalus(blockPathType) >= 0.0F;
    }

    @Override
    public Target getTarget(final double x, final double y, final double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(final Node[] neighbors, final Node pos) {
        int count = 0;

        Node south = this.findAcceptedNode(pos.x, pos.y, pos.z + 1);
        if (this.isOpen(south)) {
            neighbors[count++] = south;
        }

        Node west = this.findAcceptedNode(pos.x - 1, pos.y, pos.z);
        if (this.isOpen(west)) {
            neighbors[count++] = west;
        }

        Node east = this.findAcceptedNode(pos.x + 1, pos.y, pos.z);
        if (this.isOpen(east)) {
            neighbors[count++] = east;
        }

        Node north = this.findAcceptedNode(pos.x, pos.y, pos.z - 1);
        if (this.isOpen(north)) {
            neighbors[count++] = north;
        }

        Node up = this.findAcceptedNode(pos.x, pos.y + 1, pos.z);
        if (this.isOpen(up)) {
            neighbors[count++] = up;
        }

        Node down = this.findAcceptedNode(pos.x, pos.y - 1, pos.z);
        if (this.isOpen(down)) {
            neighbors[count++] = down;
        }

        Node southUp = this.findAcceptedNode(pos.x, pos.y + 1, pos.z + 1);
        if (this.isOpen(southUp) && hasMalus(south) && hasMalus(up)) {
            neighbors[count++] = southUp;
        }

        Node westUp = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z);
        if (this.isOpen(westUp) && hasMalus(west) && hasMalus(up)) {
            neighbors[count++] = westUp;
        }

        Node eastUp = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z);
        if (this.isOpen(eastUp) && hasMalus(east) && hasMalus(up)) {
            neighbors[count++] = eastUp;
        }

        Node northUp = this.findAcceptedNode(pos.x, pos.y + 1, pos.z - 1);
        if (this.isOpen(northUp) && hasMalus(north) && hasMalus(up)) {
            neighbors[count++] = northUp;
        }

        Node southDown = this.findAcceptedNode(pos.x, pos.y - 1, pos.z + 1);
        if (this.isOpen(southDown) && hasMalus(south) && hasMalus(down)) {
            neighbors[count++] = southDown;
        }

        Node westDown = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z);
        if (this.isOpen(westDown) && hasMalus(west) && hasMalus(down)) {
            neighbors[count++] = westDown;
        }

        Node eastDown = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z);
        if (this.isOpen(eastDown) && hasMalus(east) && hasMalus(down)) {
            neighbors[count++] = eastDown;
        }

        Node northDown = this.findAcceptedNode(pos.x, pos.y - 1, pos.z - 1);
        if (this.isOpen(northDown) && hasMalus(north) && hasMalus(down)) {
            neighbors[count++] = northDown;
        }

        Node northEast = this.findAcceptedNode(pos.x + 1, pos.y, pos.z - 1);
        if (this.isOpen(northEast) && hasMalus(north) && hasMalus(east)) {
            neighbors[count++] = northEast;
        }

        Node southEast = this.findAcceptedNode(pos.x + 1, pos.y, pos.z + 1);
        if (this.isOpen(southEast) && hasMalus(south) && hasMalus(east)) {
            neighbors[count++] = southEast;
        }

        Node northWest = this.findAcceptedNode(pos.x - 1, pos.y, pos.z - 1);
        if (this.isOpen(northWest) && hasMalus(north) && hasMalus(west)) {
            neighbors[count++] = northWest;
        }

        Node southWest = this.findAcceptedNode(pos.x - 1, pos.y, pos.z + 1);
        if (this.isOpen(southWest) && hasMalus(south) && hasMalus(west)) {
            neighbors[count++] = southWest;
        }

        Node northEastUp = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z - 1);
        if (this.isOpen(northEastUp)
                && hasMalus(northEast)
                && hasMalus(north)
                && hasMalus(east)
                && hasMalus(up)
                && hasMalus(northUp)
                && hasMalus(eastUp)) {
            neighbors[count++] = northEastUp;
        }

        Node southEastUp = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z + 1);
        if (this.isOpen(southEastUp)
                && hasMalus(southEast)
                && hasMalus(south)
                && hasMalus(east)
                && hasMalus(up)
                && hasMalus(southUp)
                && hasMalus(eastUp)) {
            neighbors[count++] = southEastUp;
        }

        Node northWestUp = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z - 1);
        if (this.isOpen(northWestUp)
                && hasMalus(northWest)
                && hasMalus(north)
                && hasMalus(west)
                && hasMalus(up)
                && hasMalus(northUp)
                && hasMalus(westUp)) {
            neighbors[count++] = northWestUp;
        }

        Node southWestUp = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z + 1);
        if (this.isOpen(southWestUp)
                && hasMalus(southWest)
                && hasMalus(south)
                && hasMalus(west)
                && hasMalus(up)
                && hasMalus(southUp)
                && hasMalus(westUp)) {
            neighbors[count++] = southWestUp;
        }

        Node northEastDown = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z - 1);
        if (this.isOpen(northEastDown)
                && hasMalus(northEast)
                && hasMalus(north)
                && hasMalus(east)
                && hasMalus(down)
                && hasMalus(northDown)
                && hasMalus(eastDown)) {
            neighbors[count++] = northEastDown;
        }

        Node southEastDown = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z + 1);
        if (this.isOpen(southEastDown)
                && hasMalus(southEast)
                && hasMalus(south)
                && hasMalus(east)
                && hasMalus(down)
                && hasMalus(southDown)
                && hasMalus(eastDown)) {
            neighbors[count++] = southEastDown;
        }

        Node northWestDown = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z - 1);
        if (this.isOpen(northWestDown)
                && hasMalus(northWest)
                && hasMalus(north)
                && hasMalus(west)
                && hasMalus(down)
                && hasMalus(northDown)
                && hasMalus(westDown)) {
            neighbors[count++] = northWestDown;
        }

        Node southWestDown = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z + 1);
        if (this.isOpen(southWestDown)
                && hasMalus(southWest)
                && hasMalus(south)
                && hasMalus(west)
                && hasMalus(down)
                && hasMalus(southDown)
                && hasMalus(westDown)) {
            neighbors[count++] = southWestDown;
        }

        return count;
    }

    private static boolean hasMalus(final @Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    private static boolean isOpen(final @Nullable Node node) {
        return node != null && !node.closed;
    }

    public void initGroundHeight(final Node node) {
        if (node.walkedDistance != 0) {
            return;
        }
        PathType belowPathType = this.getCachedPathType(node.x, node.y - 1, node.z);
        if (belowPathType.getMalus() == 0.0F) {
            Node below = getNode(node.x, node.y - 1, node.z);
            this.initGroundHeight(below);
            node.walkedDistance = below.walkedDistance + 1;
        } else {
            node.walkedDistance = 1;
        }
    }

    protected @Nullable Node findAcceptedNode(final int x, final int y, final int z) {
        Node best = null;
        PathType pathType = this.getCachedPathType(x, y, z);
        float pathCost = this.mob.getPathfindingMalus(pathType);
        if (pathCost >= 0.0F) {
            best = this.getNode(x, y, z);
            best.type = pathType;
            best.costMalus = Math.max(best.costMalus, pathCost);
            if (pathType == PathType.WALKABLE) {
                best.costMalus++;
            }
        }
        return best;
    }

    protected PathType getCachedPathType(final int x, final int y, final int z) {
        return this.pathTypeByPosCache.computeIfAbsent(
                BlockPos.asLong(x, y, z),
                key -> this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob));
    }

    @Override
    public PathType getPathTypeOfMob(final PathfindingContext context, final int x, final int y, final int z, final Mob mob) {
        // Entity is always 1x1x1: no loop needed, check single block directly
        PathType blockType = this.getPathType(context, x, y, z);
        if (blockType == PathType.DOOR_WOOD_CLOSED && this.canOpenDoors() && this.canPassDoors()) {
            blockType = PathType.WALKABLE_DOOR;
        }
        if (blockType == PathType.DOOR_OPEN && !this.canPassDoors()) {
            blockType = PathType.BLOCKED;
        }
        if (blockType == PathType.RAIL) {
            BlockPos mobPos = mob.blockPosition();
            if (this.getPathType(context, mobPos.getX(), mobPos.getY(), mobPos.getZ()) != PathType.RAIL
                    && this.getPathType(context, mobPos.getX(), mobPos.getY() - 1, mobPos.getZ()) != PathType.RAIL) {
                blockType = PathType.UNPASSABLE_RAIL;
            }
        }
        if (blockType == PathType.FENCE) {
            return PathType.FENCE;
        }
        if (blockType == PathType.UNPASSABLE_RAIL) {
            return PathType.UNPASSABLE_RAIL;
        }
        if (mob.getPathfindingMalus(blockType) < 0.0F) {
            return blockType;
        }
        if (blockType != PathType.OPEN
                && mob.getPathfindingMalus(blockType) == 0.0F
                && this.getPathType(context, x, y, z) == PathType.OPEN) {
            return PathType.OPEN;
        }
        return blockType;
    }

    @Override
    public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
        PathType blockPathType = context.getPathTypeFromState(x, y, z);
        if (blockPathType == PathType.OPEN && y >= context.level().getMinY() + 1) {
            BlockPos belowPos = new BlockPos(x, y - 1, z);
            PathType belowType = context.getPathTypeFromState(belowPos.getX(), belowPos.getY(), belowPos.getZ());
            if (belowType == PathType.DAMAGE_FIRE || belowType == PathType.LAVA) {
                blockPathType = PathType.DAMAGE_FIRE;
            } else if (belowType == PathType.DAMAGE_OTHER) {
                blockPathType = PathType.DAMAGE_OTHER;
            } else if (belowType == PathType.COCOA) {
                blockPathType = PathType.COCOA;
            } else if (belowType == PathType.FENCE) {
                if (!belowPos.equals(context.mobPosition())) {
                    blockPathType = PathType.FENCE;
                }
            } else {
                blockPathType = belowType != PathType.WALKABLE && belowType != PathType.OPEN && belowType != PathType.WATER
                        ? PathType.WALKABLE
                        : PathType.OPEN;
            }
        }

        if (blockPathType == PathType.WALKABLE || blockPathType == PathType.OPEN) {
            blockPathType = checkNeighbourBlocks(context, x, y, z, blockPathType);
        }

        return blockPathType;
    }

    private Iterable<BlockPos> iteratePathfindingStartNodeCandidatePositions(final Mob mob) {
        AABB boundingBox = ZERO_ALIGNED_BOUNDING_BOX.move(mob.getX(), mob.getY(), mob.getZ());
        return BlockPos.randomBetweenClosed(
                mob.getRandom(),
                MAX_START_NODE_CANDIDATES,
                Mth.floor(boundingBox.minX),
                Mth.floor(boundingBox.minY),
                Mth.floor(boundingBox.minZ),
                Mth.floor(boundingBox.maxX),
                Mth.floor(boundingBox.maxY),
                Mth.floor(boundingBox.maxZ));
    }

    public static PathType checkNeighbourBlocks(final PathfindingContext context, final int x, final int y, final int z, final PathType blockPathType) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        PathType pathType = context.getPathTypeFromState(x + dx, y + dy, z + dz);
                        if (pathType == PathType.DAMAGE_OTHER) {
                            return PathType.DANGER_OTHER;
                        }
                        if (pathType == PathType.DAMAGE_FIRE || pathType == PathType.LAVA) {
                            return PathType.DANGER_FIRE;
                        }
                        if (pathType == PathType.WATER) {
                            return PathType.WATER_BORDER;
                        }
                        if (pathType == PathType.DAMAGE_CAUTIOUS) {
                            return PathType.DAMAGE_CAUTIOUS;
                        }
                    }
                }
            }
        }
        return blockPathType;
    }
}
