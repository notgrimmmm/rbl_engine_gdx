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
import io.rebelengine.DYNRenderer;

/**
 *
 */
public class Scripted extends Entity{
    public final InstanceComponent instanceComponent;
    public final TransformComponent transform ;
    public final PhysicsBodyComponent body;
    public final float defaultGravity;
    
    public Scripted (DYNRenderer.RenderInstance instance, BSPFile.Model model, Vector3 origin){
        this.instanceComponent = new InstanceComponent();
        instanceComponent.instance = instance;
        instanceComponent.bspModelIndex = instance.bmIndex;
        
        this.transform = new TransformComponent();
        transform.previousPosition.set(origin);
        transform.currentPosition.set(origin);
        transform.renderPosition.set(origin);
        
        this.body = new PhysicsBodyComponent();
        
        float halfX = (model.maxX - model.minX) * 0.5f;
        float halfY = (model.maxZ - model.minZ) * 0.5f; // GL Y is Q3 Z
        float halfZ = (model.maxY - model.minY) * 0.5f; // GL Z is Q3 Y

        body.minsStand.set(-halfX , -halfY, -halfZ);
        body.maxsStand.set(halfX, halfY, halfZ);
        
        body.minsCrouch.set(body.minsStand);
        body.maxsCrouch.set(body.maxsStand);
       
        body.bspModelIndexReference = instance.bmIndex;
        body.gravity = 220f;
        body.frictionCoeff = 7f;
        body.slopeCoeff = .99f;
        this.defaultGravity = body.gravity;
        
        add(transform);
        add(body);
        add(instanceComponent);
    }
}
