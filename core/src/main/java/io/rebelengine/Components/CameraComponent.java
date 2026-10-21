package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import io.game.Behaviors.Strategy_FirstPerson;

public class CameraComponent implements Component {
    public StrategyLogic strategyLogic = new Strategy_FirstPerson(); //default is first person logic
    public PerspectiveCamera camera;
    public boolean active = true;
    public float offsetX = 0f;
    public float offsetZ = 0f;
}
