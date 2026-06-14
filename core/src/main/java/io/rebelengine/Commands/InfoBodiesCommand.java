/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Commands;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.DebugCommand;
import io.rebelengine.DebugContext;
import io.rebelengine.DebugRenderContext;

/**
 *
 */
public class InfoBodiesCommand implements DebugCommand{

    private Vector3 tmpVec = new Vector3();
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
        
        renderContext.uiBatch().setProjectionMatrix(renderContext.uiViewport().getCamera().combined);
        renderContext.uiBatch().begin();
        renderContext.uiFont().setColor(Color.WHITE);
        
        for (Entity entity : context.engine().ASHLEY.getEntitiesFor(FAMILY)){
            tmpVec.set(entity.getComponent(TransformComponent.class).renderPosition); // smooth for text
            Vector3 screenCoords = context.engine().camera.project(tmpVec);
            
            if (Float.isNaN(screenCoords.x) || Float.isNaN(screenCoords.y) || screenCoords.z < 0f) continue;
            if (screenCoords.z < 0f || screenCoords.z > 1f) continue;
            
    
            String bodyVel = entity.getComponent(PhysicsBodyComponent.class).velocity.toString();
            float trace = entity.getComponent(PhysicsBodyComponent.class).traceResult.fraction;
            String arr = entity.getComponent(PhysicsBodyComponent.class).traceResult.nonSolidOverlapIndices.toString();
            Vector3 n = entity.getComponent(PhysicsBodyComponent.class).traceResult.normal;
            renderContext.uiFont().draw(renderContext.uiBatch(),    "trace result fraction [" + trace + "]" + "\n" +
                                                                    "velocity [" + bodyVel +"]"     + "\n" +
                                                                    "normal [" +  n  + "]" + "\n" +
                                                                    "nonsolidOverlaps [" + arr + "]", 
                    screenCoords.x, screenCoords.y);
        }
        
        renderContext.uiBatch().end();
    }
    
}
