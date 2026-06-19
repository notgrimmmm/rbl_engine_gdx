/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Interactables;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPFile;
import io.rebelengine.Components.InstanceComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.DynamicRenderer;

/**
 *
 */
public class Item extends Entity{
    public Item(DynamicRenderer.RenderInstance instance, BSPFile.Model model, Vector3 origin){
        
        InstanceComponent instanceComponent = new InstanceComponent();
        instanceComponent.instance = instance;
        instanceComponent.bspModelIndex = instance.bmIndex;
        
        TransformComponent transform = new TransformComponent();
        transform.previousPosition.set(origin);
        transform.currentPosition.set(origin);
        transform.renderPosition.set(origin);
        
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        
        float halfX = (model.maxX - model.minX) * 0.5f;
        float halfY = (model.maxZ - model.minZ) * 0.5f; // GL Y is Q3 Z
        float halfZ = (model.maxY - model.minY) * 0.5f; // GL Z is Q3 Y

        physics.minsStand.set(-halfX , -halfY, -halfZ);
        physics.maxsStand.set(halfX, halfY, halfZ);
        physics.minsCrouch.set(physics.minsStand);
        physics.maxsCrouch.set(physics.maxsStand);
       
        physics.bspModelIndexReference = instance.bmIndex;
        physics.gravity = 200f;
        
        add(transform);
        add(physics);
        add(instanceComponent);

    }
}
