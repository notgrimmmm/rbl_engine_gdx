package io.rebelengine.Commands;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.Debugging.DebugCommand;
import io.rebelengine.Debugging.DebugContext;
import io.rebelengine.Debugging.DebugRenderContext;

public class InfoBodiesCommand implements DebugCommand {

    private final Vector3 tmpVec = new Vector3();
    // Cache vectors for calculating box world bounds to avoid allocations during loop
    private final Vector3 boxMins = new Vector3();
    private final Vector3 boxMaxs = new Vector3();
    
    private static final Family FAMILY = Family.all(
        TransformComponent.class,
        PhysicsBodyComponent.class
    ).get();
    
    @Override
    public String key() {
        return "info_bodies";
    }

    @Override
    public String run(String[] args, DebugContext context) {
        context.debugState().infoBodies = !context.debugState().infoBodies;
        return context.debugState().infoBodies ? "info_bodies enabled" : "info_bodies disabled";
    }

    @Override
    public void render(DebugRenderContext renderContext, DebugContext context) {
        if (!context.debugState().infoBodies) return;
        
        // ==========================================
        // 1. 3D WIREFRAME AABB RENDERING
        // ==========================================
        ShapeRenderer shapeRenderer = renderContext.shapeRenderer(); // Assuming your context exposes a ShapeRenderer
        
        shapeRenderer.setProjectionMatrix(context.engine().camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        
        for (Entity entity : context.engine().ASHLEY.getEntitiesFor(FAMILY)) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
            
            // Choose current bounding box offset based on posture state
            Vector3 mins = physics.crouching ? physics.minsCrouch : physics.minsStand;
            Vector3 maxs = physics.crouching ? physics.maxsCrouch : physics.maxsStand;
            
            // Calculate absolute world space dimensions 
            // Add min limits to current translation for bottom-left-back origin corner
            boxMins.set(transform.renderPosition).add(mins);
            boxMaxs.set(transform.renderPosition).add(maxs);
            
            float width = boxMaxs.x - boxMins.x;
            float height = boxMaxs.y - boxMins.y;
            float depth = boxMaxs.z - boxMins.z;
            
            // Draw standard bounding box wireframe green when grounded, red when airborne
            if (physics.grounded) {
                shapeRenderer.setColor(Color.GREEN);
            } else {
                shapeRenderer.setColor(Color.RED);
            }
            
            shapeRenderer.box(boxMins.x, boxMins.y, boxMins.z, width, height, -depth);
        }
        shapeRenderer.end();

        // ==========================================
        // 2. 2D TEXT OVERLAY RENDERING
        // ==========================================
        renderContext.uiBatch().setProjectionMatrix(renderContext.uiViewport().getCamera().combined);
        renderContext.uiBatch().begin();
        renderContext.uiFont().setColor(Color.WHITE);
        
        for (Entity entity : context.engine().ASHLEY.getEntitiesFor(FAMILY)){
            tmpVec.set(entity.getComponent(TransformComponent.class).renderPosition); 
            Vector3 screenCoords = context.engine().camera.project(tmpVec);
            
            if (Float.isNaN(screenCoords.x) || Float.isNaN(screenCoords.y) || screenCoords.z < 0f) continue;
            if (screenCoords.z < 0f || screenCoords.z > 1f) continue;
            
            PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
            String bodyVel = physics.velocity.toString();
            float trace = physics.traceResult.fraction;
            String arr = physics.traceResult.nonSolidOverlapIndices.toString();
            Vector3 n = physics.traceResult.normal;
            Entity hitEnt = physics.traceResult.hitEntity;
            boolean startSolid = physics.traceResult.startSolid;
            
            renderContext.uiFont().draw(renderContext.uiBatch(),    "trace result fraction [" + trace + "]" + "\n" +
                                                                    "velocity [" + bodyVel +"]"     + "\n" +
                                                                    "normal [" +  n  + "]" + "\n" +
                                                                    "nonsolidOverlaps [" + arr + "]" + "\n" +
                                                                    "hitentity [" + hitEnt + "]" + "\n" +
                                                                    "start solid [" + startSolid + "]",
                    screenCoords.x, screenCoords.y);
        }
        
        renderContext.uiBatch().end();
    }
}