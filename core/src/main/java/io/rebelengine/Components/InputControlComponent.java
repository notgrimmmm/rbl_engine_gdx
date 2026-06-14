package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;

/**
 * Marker component for entities that are eligible to receive player input.
 *
 * <p>When the {@link io.rebelengine.InputFocus input focus} is on an entity
 * with this component, that entity is the "player" for input purposes.
 *
 * <p>Presence on the entity is what makes it selectable as an input target —
 * not the entity's type. A vehicle, a spectator camera, a remote-controlled
 * drone, etc. can all opt in by adding this component.
 */
public class InputControlComponent implements Component {
    /**
     * Optional priority for focus resolution. When the engine wants to
     * auto-pick a focus target (e.g. on startup), higher wins. Defaults to 0.
     */
    public int priority = 0;
}
