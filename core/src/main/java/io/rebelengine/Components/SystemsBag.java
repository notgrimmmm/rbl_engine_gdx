/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Components;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import io.rebelengine.BSPCollision;
import io.rebelengine.BSPCollision.TraceResult;
import io.rebelengine.Components.ComponentBag.CameraComponent;
import io.rebelengine.Components.ComponentBag.PhysicsComponent;
import io.rebelengine.Components.ComponentBag.TransformComponent;

public class SystemsBag {
    // 1. CONTROL: Reads raw input, updates Entity Velocity/Rotation
    public static class ControlSystem extends IteratingSystem {
        private EngineInput input;
        public ControlSystem(EngineInput input) { super(Family.all(PhysicsComponent.class, CameraComponent.class).get()); this.input = input; }

        @Override
        protected void processEntity(Entity e, float dt) {
            CameraComponent cam = e.getComponent(CameraComponent.class);
            PhysicsComponent phys = e.getComponent(PhysicsComponent.class);
            if (!cam.isActive) return;

            // Rotation
            cam.yaw += input.deltaX * 0.2f;
            cam.pitch -= input.deltaY * 0.2f;
            cam.pitch = Math.max(-89f, Math.min(89f, cam.pitch));

            // Translation (Intent)
            float cos = (float)Math.cos(Math.toRadians(cam.yaw)), sin = (float)Math.sin(Math.toRadians(cam.yaw));
            phys.vel.x = 0; phys.vel.z = 0;
            if (input.keys[Input.Keys.W]) { phys.vel.x += sin; phys.vel.z -= cos; }
            if (input.keys[Input.Keys.S]) { phys.vel.x -= sin; phys.vel.z += cos; }
            if (phys.vel.len2() > 0) phys.vel.nor().scl(120f);
            if (phys.onGround && input.keys[Input.Keys.SPACE]) { phys.vel.y = 170f; phys.onGround = false; }
        }
    }

    // 2. PHYSICS: Slaps the Box against the BSP
    public static class bspPhysicsSystem extends IteratingSystem {
        private BSPCollision col;
        public bspPhysicsSystem(BSPCollision col) { super(Family.all(PhysicsComponent.class, TransformComponent.class).get()); this.col = col; }

        @Override
        protected void processEntity(Entity e, float dt) {
            PhysicsComponent p = e.getComponent(PhysicsComponent.class);
            TransformComponent t = e.getComponent(TransformComponent.class);

            if (!p.onGround) p.vel.y -= 800f * dt;
            t.prevPos.set(t.pos);
            col.slideMove(t.pos, p.vel, p.mins, p.maxs, dt);
            p.onGround = col.isOnGround(t.pos, p.mins, p.maxs, new TraceResult());
            if (p.onGround && p.vel.y < 0) p.vel.y = 0;
        }
    }

    // 3. RENDER SYNC: Positions the Camera & Interpolates everything
    public static class RenderSyncSystem extends IteratingSystem {
        private PerspectiveCamera mainCam;
        public float alpha;
        public RenderSyncSystem(PerspectiveCamera cam) { super(Family.all(TransformComponent.class).get()); this.mainCam = cam; }

        @Override
        protected void processEntity(Entity e, float dt) {
            TransformComponent t = e.getComponent(TransformComponent.class);
            t.renderPos.set(t.prevPos).lerp(t.pos, alpha);

            CameraComponent c = e.getComponent(CameraComponent.class);
            if (c != null && c.isActive) {
                mainCam.position.set(t.renderPos.x, t.renderPos.y + c.offset, t.renderPos.z);
                float yR = (float)Math.toRadians(c.yaw), pR = (float)Math.toRadians(c.pitch);
                float cp = (float)Math.cos(pR);
                mainCam.direction.set(cp * (float)Math.sin(yR), (float)Math.sin(pR), -cp * (float)Math.cos(yR));
                mainCam.update();
            }
        }
    }
}
