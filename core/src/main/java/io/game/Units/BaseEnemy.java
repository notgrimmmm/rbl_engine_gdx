/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Units;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.rebelengine.Components.InstanceComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.ScriptComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.DYNRenderer;
import io.rebelengine.Helpers.EntityFlags;
import io.rebelengine.NavMeshGraph;

/**
 *
 */
public class BaseEnemy extends Entity{
    public final InstanceComponent instanceComponent;
    public final TransformComponent transform ;
    public final PhysicsBodyComponent body;
    public final ScriptComponent scriptComponent;
    public final NavMeshGraph NAV_GRAPH;
    public final Model modelGLB;
    
    public BaseEnemy (DYNRenderer.RenderInstance instance, Model model, Vector3 origin, NavMeshGraph graph){
        flags =  EntityFlags.ENEMY | EntityFlags.INITIATOR;
        
        this.NAV_GRAPH = graph;
        this.scriptComponent = new ScriptComponent();
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
        
        float halfX = 16;
        float halfY = 48;
        float halfZ = 16;

        body.minsStand.set(-halfX , -halfY, -halfZ);
        body.maxsStand.set(halfX, halfY, halfZ);
        
        body.minsCrouch.set(-halfX, -halfZ, -halfY);
        body.maxsCrouch.set(halfX, halfZ, halfY);
       
        body.bspModelIndexReference = instance.bmIndex;
        body.gravity = 220f;
        body.Physics = body.Physics.Mutable;
        
        add(transform);
        add(body);
        add(instanceComponent);
        add(scriptComponent);
    }
}