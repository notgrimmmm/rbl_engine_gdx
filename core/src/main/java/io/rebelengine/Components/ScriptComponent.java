/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

/**
 *
 */
public class ScriptComponent implements Component{
    public final Array<EntityScript> behaviors = new Array<>();
    
    /**
     * This is a de-reference meaning you can do [ new SriptComponent() .add .add .add ; ]
     * @param script
     * @return 
     */
    public ScriptComponent add(EntityScript script){
        for (EntityScript behavior : behaviors) {
            if (behavior.getClass() == script.getClass()) {
                return this; // Reject duplicate script types
            }
        }
        behaviors.add(script);
        System.out.println(script.getClass());
        return this;
    }
}
