package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.RebelAshleyEngine;

public class LocomotionSystem extends EntitySystem implements RebelAshleyEngine.FixedUpdatable {
    private static final Family FAMILY = Family.all(
        TransformComponent.class,
        PhysicsBodyComponent.class,
        InputControlComponent.class
    ).get();

    private ImmutableArray<Entity> entities;

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void fixedUpdate(float fixedDt) {
        for (Entity entity : entities) {
            if (entity.getComponent(InputControlComponent.class).locomotionBehavior == null) continue;
            entity.getComponent(InputControlComponent.class).locomotionBehavior.fixedUpdate(entity);
        }
    }
}
