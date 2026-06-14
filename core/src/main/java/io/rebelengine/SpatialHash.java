package io.rebelengine;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;

import java.util.*;

/**
 * A spatial hash for broadphase collision detection.
 * Partitions entities into 3D grid cells to avoid O(N^2) checks.
 */
public class SpatialHash {
    private final float cellSize;
    private final Map<Long, List<Entity>> grid = new HashMap<>();
    private final Set<Entity> resultBuffer = new HashSet<>();

    public SpatialHash(float cellSize) {
        this.cellSize = cellSize;
    }

    /**
     * Clear the hash. Call this at the start of every physics update.
     */
    public void clear() {
        for (List<Entity> list : grid.values()) {
            list.clear();
        }
    }

    /**
     * Add an entity to the spatial hash based on its current AABB.
     */
    public void insert(Entity entity) {
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);
        
        Vector3 mins = body.crouching ? body.minsCrouch : body.minsStand;
        Vector3 maxs = body.crouching ? body.maxsCrouch : body.maxsStand;

        int minX = (int) Math.floor((transform.currentPosition.x + mins.x) / cellSize);
        int minY = (int) Math.floor((transform.currentPosition.y + mins.y) / cellSize);
        int minZ = (int) Math.floor((transform.currentPosition.z + mins.z) / cellSize);
        
        int maxX = (int) Math.floor((transform.currentPosition.x + maxs.x) / cellSize);
        int maxY = (int) Math.floor((transform.currentPosition.y + maxs.y) / cellSize);
        int maxZ = (int) Math.floor((transform.currentPosition.z + maxs.z) / cellSize);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long key = hash(x, y, z);
                    grid.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
                }
            }
        }
    }

    /**
     * Finds all unique entities in the cells intersected by the swept AABB.
     */
    public Set<Entity> query(Vector3 start, Vector3 end, Vector3 mins, Vector3 maxs) {
        resultBuffer.clear();
        
        // Calculate the bounding box of the entire sweep path
        float bMinX = Math.min(start.x, end.x) + mins.x;
        float bMinY = Math.min(start.y, end.y) + mins.y;
        float bMinZ = Math.min(start.z, end.z) + mins.z;
        
        float bMaxX = Math.max(start.x, end.x) + maxs.x;
        float bMaxY = Math.max(start.y, end.y) + maxs.y;
        float bMaxZ = Math.max(start.z, end.z) + maxs.z;

        int minX = (int) Math.floor(bMinX / cellSize);
        int minY = (int) Math.floor(bMinY / cellSize);
        int minZ = (int) Math.floor(bMinZ / cellSize);
        
        int maxX = (int) Math.floor(bMaxX / cellSize);
        int maxY = (int) Math.floor(bMaxY / cellSize);
        int maxZ = (int) Math.floor(bMaxZ / cellSize);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long key = hash(x, y, z);
                    List<Entity> cellEntities = grid.get(key);
                    if (cellEntities != null) {
                        resultBuffer.addAll(cellEntities);
                    }
                }
            }
        }
        return resultBuffer;
    }

    private long hash(int x, int y, int z) {
        // Simple bit-packing for a 3D hash key
        return ((long) x << 42) | ((long) (y & 0x1FFFFF) << 21) | (long) (z & 0x1FFFFF);
    }
}
