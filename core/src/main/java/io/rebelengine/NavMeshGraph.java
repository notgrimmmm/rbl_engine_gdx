package io.rebelengine;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.*;

public class NavMeshGraph {

    public static class NavNode {
        public int id;
        public Vector3 v0, v1, v2;
        public Vector3 center = new Vector3();
        public Array<NavNode> neighbors = new Array<>();

        public NavNode(int id, Vector3 v0, Vector3 v1, Vector3 v2) {
            this.id = id;
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.center.set(v0).add(v1).add(v2).scl(1f / 3f);
        }

        public boolean contains(Vector3 p) {
            boolean inTriangle = Intersector.isPointInTriangle(
                p.x, p.z,
                v0.x, v0.z,
                v1.x, v1.z,
                v2.x, v2.z
            );
            return inTriangle && Math.abs(p.y - center.y) < 64f;
        }
    }

    private final Array<NavNode> nodes = new Array<>();

    public NavMeshGraph(NavMeshExtractor.NavMeshGeometry geometry) {
        if (geometry == null || geometry.indices.size == 0 || geometry.allVertices.size == 0) return;

        // 1. Build nodes from geometry
        for (int i = 0; i < geometry.indices.size; i += 3) {
            Vector3 v0 = geometry.allVertices.get(geometry.indices.get(i) & 0xFFFF);
            Vector3 v1 = geometry.allVertices.get(geometry.indices.get(i + 1) & 0xFFFF);
            Vector3 v2 = geometry.allVertices.get(geometry.indices.get(i + 2) & 0xFFFF);
            nodes.add(new NavNode(i / 3, v0, v1, v2));
        }

        // 2. Link adjacent triangles
        for (int i = 0; i < nodes.size; i++) {
            NavNode a = nodes.get(i);
            for (int j = i + 1; j < nodes.size; j++) {
                NavNode b = nodes.get(j);
                if (sharesEdge(a, b)) {
                    a.neighbors.add(b);
                    b.neighbors.add(a);
                }
            }
        }

//        System.out.println("[NavMeshGraph] Initialized graph with " + nodes.size + " triangle nodes.");
//        for (NavNode n : nodes) {
//            System.out.println("  -> Node " + n.id + " Center: " + n.center + " Neighbors: " + n.neighbors.size);
//        }
    }

    private boolean sharesEdge(NavNode a, NavNode b) {
        int shared = 0;
        Vector3[] aV = {a.v0, a.v1, a.v2};
        Vector3[] bV = {b.v0, b.v1, b.v2};

        for (Vector3 va : aV) {
            for (Vector3 vb : bV) {
                // Increased tolerance: dst2 < 1024.0f (within ~32 units in BSP space)
                if (va.dst2(vb) < 1024.0f) {
                    shared++;
                    break;
                }
            }
        }

        // Standard edge match (2 common vertices) OR fallback centroid proximity match
        if (shared >= 2) return true;
        
        // Proximity fallback if floor brush seams have minor gaps
        return a.center.dst2(b.center) < 16384.0f && Math.abs(a.center.y - b.center.y) < 32.0f;
    }

    public NavNode findClosestNode(Vector3 point) {
        NavNode best = null;
        float minDst = Float.MAX_VALUE;

        for (NavNode node : nodes) {
            if (node.contains(point)) return node;

            float dx = node.center.x - point.x;
            float dz = node.center.z - point.z;
            float dst = dx * dx + dz * dz;

            if (dst < minDst) {
                minDst = dst;
                best = node;
            }
        }
        return best;
    }

    public Array<Vector3> findPath(Vector3 start, Vector3 target) {
        Array<Vector3> waypoints = new Array<>();

        if (nodes.size == 0) {
            waypoints.add(new Vector3(start));
            waypoints.add(new Vector3(target));
            return waypoints;
        }

        NavNode startNode = findClosestNode(start);
        NavNode targetNode = findClosestNode(target);

//        System.out.println("[NavMeshGraph] findPath: StartNode=" + (startNode != null ? startNode.id : "null") 
//                + " -> TargetNode=" + (targetNode != null ? targetNode.id : "null"));

        if (startNode == null || targetNode == null) {
            waypoints.add(new Vector3(start));
            waypoints.add(new Vector3(target));
            return waypoints;
        }

        if (startNode == targetNode) {
            waypoints.add(new Vector3(start));
            waypoints.add(new Vector3(target));
            return waypoints;
        }

        Map<NavNode, NavNode> cameFrom = new HashMap<>();
        Map<NavNode, Float> gScore = new HashMap<>();
        Set<NavNode> openSetSet = new HashSet<>();
        
        PriorityQueue<NavNode> openSet = new PriorityQueue<>(
            Comparator.comparingDouble(n -> gScore.getOrDefault(n, Float.MAX_VALUE) + n.center.dst(target))
        );

        gScore.put(startNode, 0f);
        openSet.add(startNode);
        openSetSet.add(startNode);

        while (!openSet.isEmpty()) {
            NavNode current = openSet.poll();
            openSetSet.remove(current);

            if (current == targetNode) {
                Array<NavNode> pathNodes = new Array<>();
                NavNode curr = current;
                pathNodes.add(curr);
                while (cameFrom.containsKey(curr)) {
                    curr = cameFrom.get(curr);
                    pathNodes.add(curr);
                }
                pathNodes.reverse();

                waypoints.add(new Vector3(start));
                for (NavNode n : pathNodes) {
                    waypoints.add(new Vector3(n.center));
                }
                waypoints.add(new Vector3(target));
                return waypoints;
            }

            for (NavNode neighbor : current.neighbors) {
                float tentativeG = gScore.get(current) + current.center.dst(neighbor.center);
                if (tentativeG < gScore.getOrDefault(neighbor, Float.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);

                    if (openSetSet.contains(neighbor)) {
                        openSet.remove(neighbor);
                    }
                    openSet.add(neighbor);
                    openSetSet.add(neighbor);
                }
            }
        }

        //System.out.println("[NavMeshGraph] WARNING: No connected path found between StartNode " + startNode.id + " and TargetNode " + targetNode.id);
        waypoints.add(new Vector3(start));
        waypoints.add(new Vector3(target));
        return waypoints;
    }
}