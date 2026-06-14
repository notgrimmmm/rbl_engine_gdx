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
    
    public ScriptComponent add(EntityScript script){
        behaviors.add(script);
        return this;
    }
}
