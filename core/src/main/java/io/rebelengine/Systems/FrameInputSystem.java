package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.MouseLookComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.InputFocus;
import io.rebelengine.RebelAshleyEngine;

public class FrameInputSystem extends EntitySystem implements RebelAshleyEngine.FrameUpdatable {
    private static final Family FAMILY = Family.all(
        InputControlComponent.class,
        TransformComponent.class,
        MouseLookComponent.class
    ).get();

    private ImmutableArray<Entity> entities;

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void frameUpdate() {
        for (Entity entity : entities) {
            // InputFocus is the single source of truth. If this entity is not
            // the focused one, or input is globally locked, do nothing — and
            // also clear any stale mouse deltas so they don't snap in on regain.
            if (!InputFocus.is(entity)) {
                MouseLookComponent ml = entity.getComponent(MouseLookComponent.class);
                ml.accumulatedDeltaX = 0f;
                ml.accumulatedDeltaY = 0f;
                continue;
            }
            
            // run specific based behavior let it handle gathering components
            if (entity.getComponent(InputControlComponent.class).inputBehavior == null) continue;
            entity.getComponent(InputControlComponent.class).inputBehavior.update(entity);
        }
    }
}
