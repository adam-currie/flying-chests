package com.fjjy.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import com.fjjy.FlyingChests;
import com.fjjy.Util;
import com.fjjy.mixin.PathAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.phys.Vec3;

/**
 * Augments Pathfinder to temporarily avoid a threat instead of targeting a fixed goal.
 */
public class FleePathFinder extends PathFinder {
    // we use the Node class a little differently than A*:
    // +---------------------+------------------------+---------------+-----------------+-------------+
    // | Node      |         |                        |                                               |
    // +-----------+         |                        | Used for choosing...                          |
    // | Property            | Description            +-----------------------------------------------+
    // |                     |                        | Node.cameFrom | Scanning Order  | destination |
    // +---------------------+------------------------+---------------+-----------------+-------------+
    // | Node.g              | full path travel cost  |      Yes      |       Yes       |     Yes     |
    // | Node.h              | full path threat cost  |      Yes      |       No        |     Yes     | 
    // | Node.f (open node)  | scanning order fitness |      No       |       Yes       |     No      |
    // | Node.f (closed node)| destination fitness    |      No       |       No        |     Yes     |
    // +---------------------+------------------------+---------------+-----------------+-------------+
    // Node.f has to be updated when closing a node to reflect its new purpose.

    private final NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();
    private final Node[] neighbors = new Node[32];

    private int maxNodes;
    private Vec3 threatPos = Vec3.ZERO;
    private float goalDistance;
    private boolean fleeMode = false;
    private BooleanSupplier captureDebug = () -> false;

    public FleePathFinder(NodeEvaluator nodeEvaluator, int maxNodes) {
        super(nodeEvaluator, maxNodes);
        this.nodeEvaluator = nodeEvaluator;
        this.maxNodes = maxNodes;
    }

    @Override
    public void setMaxVisitedNodes(int maxVisitedNodes) {
        super.setMaxVisitedNodes(maxVisitedNodes);
        maxNodes = maxVisitedNodes;
    }

    @Override
    public void setCaptureDebug(BooleanSupplier captureDebug) {
        super.setCaptureDebug(captureDebug);
        this.captureDebug = captureDebug;
    }

    private float threatCost(float threatDistanceSqr) {
        return 128f/(threatDistanceSqr + 6f);        
    }

    /*
     * Evaluates a node for flee path finding based on its distance from a threat.
     */
    private float threatCost(Node node) {
        float x = Math.abs((float)(node.x + .5 - threatPos.x));
        float z = Math.abs((float)(node.z + .5 - threatPos.z));
        return threatCost(x*x + z*z);
    }

    /*
     * Calculates the approximate euclidean distance from the threat.
     */
    private float threatDistance(Node node) {
        float x = Math.abs((float)(node.x + .5 - threatPos.x));
        float z = Math.abs((float)(node.z + .5 - threatPos.z));
        return Util.fastApproxSqrt(x*x + z*z);
    }

    /**
     * Enables flee mode with the given threat position and goal distance,
     * temporarily augmenting pathfinding to avoid the threat instead of targeting a fixed goal.
     * 
     * @param threatPos the position to flee from
     * @param goalDistance after this distance the pathfinder will stop searching,
     * but this is a lower bound because traversal cost will be factored in and
     * effectively increasing the distance goal beyond this.
     * if the entity is 5 blocks away and then moves directly away unimpeded,
     * the actual goal distance will be around the same as the lower bound.
     */
    public void startFleeing(Vec3 threatPos, float goalDistance) {
        this.threatPos = threatPos;
        this.goalDistance = goalDistance;
        this.fleeMode = true;
    }

    public void stopFleeing() {
        this.fleeMode = false;
    }

    private static final float[] DIAGONAL_FACTORS = {1f, (float)Math.sqrt(2), (float)Math.sqrt(3)};

    /**
     * Distance between neighboring nodes, used to calculate g score of a path.
     * For non neighbors the result may not be useful.
     */
    private static float getNeighborDistance(Node a, Node b) {
        int diagonalicity = (a.x != b.x ? 1 : 0) +
                            (a.y != b.y ? 1 : 0) +
                            (a.z != b.z ? 1 : 0) - 1;
        return DIAGONAL_FACTORS[diagonalicity];
    }

    private float scanningOrderFitness(float g, float threatDistance) {
        return g - threatDistance*1.2f; // distance is scaled up a bit to account for sqrt error and err on the side of fleeing further. 
    }

    private void initFirstNodeWeights(Node start) {
        start.h = threatCost(start);
        start.g = // g is the cost, excluding threat distance cost of the path to this point
                (start.costMalus + 1) // per node cost...
                // ...normally this is scaled by distance from prev node,
                // but we dont have a prev node and we also need to just make this a heuristic element
                // anyway as it's only purpose is adjusting how likely the entity is to just stay put
                * threatCost(start);//todo: scalar on this + a constant probably
        start.f = scanningOrderFitness(start.g, threatDistance(start));
    }

    /**
     * Fleeing is driven by flee parameters set via {@link #startFleeing}.
     * Call stopFleeing() to return to normal pathfinding behavior.
     * Uses a heavily modified version of A* that avoids a position instead of targeting a position.
     */
    @Override
    public Path findPath(
        PathNavigationRegion region,
        Mob mob,
        Set<BlockPos> targets,
        float maxPathLength,
        int reachRange,
        float maxVisitedNodesMultiplier
    ) {
        if (!fleeMode) {
            return super.findPath(region, mob, targets, maxPathLength, reachRange, maxVisitedNodesMultiplier);
        }
        long startNs = System.nanoTime();
        openSet.clear();
        nodeEvaluator.prepare(region, mob);
        Node best = nodeEvaluator.getStart();
        if (best == null) {
            nodeEvaluator.done();
            return null;
        }

        initFirstNodeWeights(best);
        openSet.insert(best);

        int maxVisitedNodesAdjusted = (int)(this.maxNodes * maxVisitedNodesMultiplier * 2); // *2 because we need more for fleeing since its open ended
        boolean doCapture = captureDebug.getAsBoolean();
        Set<Node> closedSet = doCapture ? new HashSet<>() : Set.of();

        for (int i = 0; i < maxVisitedNodesAdjusted && !openSet.isEmpty(); i++) {
            Node current = openSet.pop();
            current.closed = true;
            if (doCapture) closedSet.add(current);

            // once a node closed, it's f is no longer for queue order 
            // and is instead just used for choosing a destination node 
            // so now we incorporate accumulated threat cost into f which we left out while in the open set.
            float openF = current.f;
            current.f = current.f + current.h;

            if (current.f > best.f) {
                best = current;
                // f is already initialized to g - threatDistance so we can extract that back here.
                float threatDistance = best.g - openF;
                if (threatDistance > goalDistance) {
                    break;
                }
            }

            int neighborCount = nodeEvaluator.getNeighbors(neighbors, current);// todo: 26 - neighborCount tells you the number of blocked adjacent nodes which can be used to avoid them (increase cost)
            for (int n = 0; n < neighborCount; n++) {
                Node neighbor = neighbors[n];
                if (neighbor == null || neighbor.closed || neighbor.costMalus < 0.0f) {
                    continue;
                }

                //debug: ignore vertical moves
                if (neighbor.y != current.y) {
                    continue;
                }

                float threatCost = threatCost(neighbor);
                float accumulatedH = current.h + threatCost;
                float g = // g is the cost, excluding threat distance cost of the path to this point
                    current.g + // existing cost of the path
                        (neighbor.costMalus + 1) // per node cost...
                        * getNeighborDistance(current, neighbor);// ...scaled by length to account for more distance traveled on diagonals
                float scanningOrderFitness = scanningOrderFitness(g, threatDistance(neighbor));
                if (neighbor.inOpenSet()) {
                    // for choosing cameFrom we want to consider the actual cost of the path,
                    // not ignoring accumulated threat cost like we do for f values in the open set.
                    // in the open set we ignore accumulated threat cost so that scanning order is
                    // more focused on just fleeing directly away 
                    // (more efficient than breadth-first which would be the effective scanning order if we included accumulated threat costs in f for open set popping)
                    if (g + accumulatedH < neighbor.g + neighbor.h) {
                        neighbor.cameFrom = current;
                        neighbor.g = g;
                        neighbor.h = accumulatedH;
                        openSet.changeCost(neighbor, scanningOrderFitness);
                    }
                } else {
                    //todo: DRY this (similar to above)
                    neighbor.cameFrom = current;
                    neighbor.g = g;
                    neighbor.h = accumulatedH;
                    neighbor.f = scanningOrderFitness;
                    openSet.insert(neighbor);
                }
            }
        }

        FlyingChests.LOGGER.info("FleePathFinder.findPath took {} us", (System.nanoTime() - startNs) / 1000);
        Target fakeTarget = doCapture ? nodeEvaluator.getTarget(best.x, best.y, best.z) : null;
        nodeEvaluator.done();
        Path result = reconstructPath(best);
        if (doCapture) {
            fakeTarget.setReached();
            ((PathAccessor)(Object) result).invokeSetDebug(openSet.getHeap(), closedSet.toArray(Node[]::new), Set.of(fakeTarget));
        }
        return result;
    }

    private Path reconstructPath(Node end) {
        List<Node> nodes = new ArrayList<>();
        for (Node node = end; node != null; node = node.cameFrom) {
            nodes.add(node);
        }
        Collections.reverse(nodes);
        return new Path(nodes, end.asBlockPos(), nodes.size() > 1);
    }

}
