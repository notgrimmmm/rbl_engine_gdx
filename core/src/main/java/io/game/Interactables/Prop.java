/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Interactables;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.rebelengine.BSPFile;
import io.rebelengine.Components.InstanceComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.DYNRenderer;

/**
 *
 */
public class Prop extends Entity{
    public final InstanceComponent instanceComponent;
    public final TransformComponent transform ;
    public final PhysicsBodyComponent body;
    public final Model modelGLB;
    
    public Prop (DYNRenderer.RenderInstance instance, BSPFile.Model model, Vector3 origin){
        this.instanceComponent = new InstanceComponent();
        instanceComponent.instance = instance;
        instanceComponent.bspModelIndex = instance.bmIndex;
        
        this.transform = new TransformComponent();
        transform.previousPosition.set(origin);
        transform.currentPosition.set(origin);
        transform.renderPosition.set(origin);
        
        this.body = new PhysicsBodyComponent();
        this.modelGLB = null;//not glb dont set this
        
        float halfX = (model.maxX - model.minX) * 0.5f;
        float halfY = (model.maxZ - model.minZ) * 0.5f; // GL Y is Q3 Z
        float halfZ = (model.maxY - model.minY) * 0.5f; // GL Z is Q3 Y

        body.minsStand.set(-halfX , -halfY, -halfZ);
        body.maxsStand.set(halfX, halfY, halfZ);
        
        body.minsCrouch.set(-halfX, -halfZ, -halfY);
        body.maxsCrouch.set(halfX, halfZ, halfY);
       
        body.bspModelIndexReference = instance.bmIndex;
        body.gravity = 220f;
        body.frictionCoeff = 7f;
        body.slopeCoeff = .99f;
        
        add(transform);
        add(body);
        add(instanceComponent);
    }
    
    /**
     * 
     * @param instance
     * @param model - glb model
     * @param origin 
     */
    public Prop (DYNRenderer.RenderInstance instance, Model model, Vector3 origin){
        this.instanceComponent = new InstanceComponent();
        instanceComponent.instance = instance;
        instanceComponent.bspModelIndex = instance.bmIndex;
        
        this.transform = new TransformComponent();
        transform.previousPosition.set(origin);
        transform.currentPosition.set(origin);
        transform.renderPosition.set(origin);
        
        this.body = new PhysicsBodyComponent();
        
        this.modelGLB = model;
        BoundingBox bounds = new BoundingBox();
        model.calculateBoundingBox(bounds);
        
        float halfX = (bounds.max.x - bounds.min.x) * 0.5f;
        float halfY = (bounds.max.y - bounds.min.y) * 0.5f;
        float halfZ = (bounds.max.z - bounds.min.y) * 0.5f;

        body.minsStand.set(-halfX , -halfY, -halfZ);
        body.maxsStand.set(halfX, halfY, halfZ);
        
        body.minsCrouch.set(-halfX, -halfZ, -halfY);
        body.maxsCrouch.set(halfX, halfZ, halfY);
       
        body.bspModelIndexReference = instance.bmIndex;
        body.gravity = 220f;
        body.frictionCoeff = 7f;
        body.slopeCoeff = .99f;
        
        add(transform);
        add(body);
        add(instanceComponent);
    }
}
