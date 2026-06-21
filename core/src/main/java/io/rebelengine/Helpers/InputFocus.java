package io.rebelengine.Helpers;

import com.badlogic.ashley.core.Entity;

/**
 * Single source of truth for "which entity currently receives player input."
 *
 * <p>Static API by design — any system or processor can consult it without
 * dependency plumbing. Only one entity can be focused at a time. The
 * {@link #setLocked(boolean) lock} flag globally silences all input (used by
 * the debug console, pause menus, cutscenes, etc.).
 *
 * <p>Usage:
 * <pre>{@code
 *   InputFocus.set(playerEntity);
 *   InputFocus.setLocked(true);   // silence everything
 *   InputFocus.setLocked(false);
 *   InputFocus.clear();           // no one is focused
 * }</pre>
 *
 * <p>Consumers should always check both {@link #isLocked()} and {@link #is(Entity)}
 * before reading input. When the focused entity changes, the previous focus is
 * cleared automatically.
 */
public final class InputFocus {

    private static Entity focused;
    private static boolean locked;

    private InputFocus() {}

    /** Set the entity that should receive input. Pass {@code null} to clear. */
    public static void set(Entity entity) {
        focused = entity;
    }

    /** Equivalent to {@code set(null)}. */
    public static void clear() {
        focused = null;
    }

    /**
     * Globally silence input. Use for the debug console, pause menus,
     * cutscenes, modal dialogs, etc.
     */
    public static void setLocked(boolean locked) {
        InputFocus.locked = locked;
    }

    public static boolean isLocked() {
        return locked;
    }

    /** @return the currently focused entity, or {@code null}. */
    public static Entity get() {
        return focused;
    }

    /**
     * @return true if {@code entity} is the focused entity AND input is not
     *         globally locked. This is the gate every input system should use.
     */
    public static boolean is(Entity entity) {
        return !locked && entity != null && entity == focused;
    }
}
