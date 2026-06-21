/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Helpers;

/**
 *
 */
public class EntityFlags {
    public static final int PLAYER      = 1;
    public static final int ENEMY       = 1 << 1;
    public static final int INITIATOR   = 1 << 2;
    public static final int PROJECTILE  = 1 << 3;
}
