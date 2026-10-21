/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package io.rebelengine.Components;

/**
 * Logic for camera syncing behavior
 */
public interface StrategyLogic {
    public void update(PhysicsBodyComponent physics, TransformComponent transform, CameraComponent cameraComponent);
}
