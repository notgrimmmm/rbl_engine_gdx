package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import io.rebelengine.Components.InstanceComponent;
import io.rebelengine.RebelAshleyEngine;
import io.rebelengine.Components.TransformComponent;

public class TransformInterpolationSystem extends EntitySystem implements RebelAshleyEngine.AlphaUpdatable {
    private static final Family FAMILY = Family.all(TransformComponent.class).get();
    private ImmutableArray<Entity> entities;
    
    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void alphaUpdate(float alpha) {
        for (Entity entity : entities) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            transform.renderPosition.set(transform.previousPosition).lerp(transform.currentPosition, alpha);
            
            float yawSmooth = MathUtils.lerp(transform.previousYaw, transform.currentYaw, alpha);
            transform.renderYaw = yawSmooth;
            
            float pitchSmooth = MathUtils.lerp(transform.previousPitch, transform.currentPitch, alpha);
            transform.renderPitch = pitchSmooth;

            InstanceComponent instanceComp = entity.getComponent(InstanceComponent.class);
            if (instanceComp != null && instanceComp.instance != null) {
                instanceComp.instance.position.set(transform.renderPosition);
                // GDX uses Y for Yaw, X for Pitch in standard 3D rotation order
                if (!instanceComp.override_rotation) instanceComp.instance.rotation.set(transform.renderPitch, transform.renderYaw, 0f);
            }
        }
    }
}
