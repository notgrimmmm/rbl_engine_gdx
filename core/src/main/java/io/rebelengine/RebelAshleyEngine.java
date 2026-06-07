package io.rebelengine;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;

public class RebelAshleyEngine extends Engine {
    public void updateFrame() {
        for (EntitySystem system : getSystems()) {
            if (system instanceof FrameUpdatable frameSystem) {
                frameSystem.frameUpdate();
            }
        }
    }

    public void updateFixed(float fixedDt) {
        for (EntitySystem system : getSystems()) {
            if (system instanceof FixedUpdatable fixedSystem) {
                fixedSystem.fixedUpdate(fixedDt);
            }
        }
    }

    public void updateRender(float alpha) {
        for (EntitySystem system : getSystems()) {
            if (system instanceof AlphaUpdatable alphaSystem) {
                alphaSystem.alphaUpdate(alpha);
            }
        }
    }

    public interface FixedUpdatable {
        void fixedUpdate(float fixedDt);
    }

    public interface FrameUpdatable {
        void frameUpdate();
    }

    public interface AlphaUpdatable {
        void alphaUpdate(float alpha);
    }
}
