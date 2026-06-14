/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game;

import com.badlogic.ashley.core.Entity;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.GameContext;
import io.rebelengine.Info_Entity;

/**
 *
 */
public class PlayerScriptTest implements EntityScript{
    private final Entity owner;
    private PhysicsBodyComponent body;
    
    public PlayerScriptTest(Entity owner){
        this.owner = owner;
    }
    
    @Override
    public void execute(){
        body = owner.getComponent(PhysicsBodyComponent.class);
        if (body.traceResult.nonSolidOverlapIndices.isEmpty()) return;

        // find the trigger we just overlapped
        for (Integer triggerInt : body.traceResult.nonSolidOverlapIndices){
            if (triggerInt == null) continue; // check for null since its an Integer and not int (big difference)
            int index = triggerInt; // convert to int now
            
            Info_Entity.Trigger trigger = GameContext.get().get_trigger(index);
            if (trigger.sleep) continue; // cant access the trigger right now
            
            
        }        
    }
}
