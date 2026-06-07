package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;

public class MouseLookComponent implements Component {
    public float accumulatedDeltaX;
    public float accumulatedDeltaY;
    public float sensitivity = 0.2f;
    public boolean active = true;
    public boolean hasLastMousePosition;
    public int lastMouseX;
    public int lastMouseY;
}
