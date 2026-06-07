package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;

public class PlayerInputComponent implements Component {
    public float moveForward;
    public float moveRight;

    public boolean active = true;
    public boolean crouchHeld;
    public boolean jumpQueued;
}
