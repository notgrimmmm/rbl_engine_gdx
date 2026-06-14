package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
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
            transform.renderYaw = transform.currentYaw;
            transform.renderPitch = transform.currentPitch;
        }
    }
}
