/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Components.ScriptComponent;
import io.rebelengine.Helpers.EntityFlags;
import io.rebelengine.RebelAshleyEngine;

/**
 *
 */
public class ScriptingSystem extends EntitySystem implements RebelAshleyEngine.FixedUpdatable{
    private static final Family FAMILY = Family.all(
            ScriptComponent.class
    ).get();

    private ImmutableArray<Entity> entities;

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }
    
    @Override
    public void fixedUpdate(float fixedDt) {
        for (Entity entity : entities){
            // if the entity does NOT have the INITIATOR flag skip
            if ((entity.flags & EntityFlags.INITIATOR) == 0) continue;
            
            ScriptComponent scriptComp = entity.getComponent(ScriptComponent.class);
            
            for (EntityScript behavior : scriptComp.behaviors){
                behavior.update_fixed(fixedDt);
            }
        }
    }
    
    public int get_initatorCount(){
     if (entities == null) return 0;
     int count = 0;
     for (Entity entity : entities){
         if ((entity.flags & EntityFlags.INITIATOR) != 0) count++;
     }
     return count;
    }
    
}
