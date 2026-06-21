/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Instances;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import games.rednblack.miniaudio.MAAttenuationModel;
import games.rednblack.miniaudio.MAAudioBuffer;
import games.rednblack.miniaudio.MASound;
import io.rebelengine.EngineContext;

/**
 * Instance sound effect
 */
public class SFX {
    private final MASound sound;
    private final MAAudioBuffer decoded_buffer;
    public SFX(String fileName){
        FileHandle fileHandle = Gdx.files.internal("sounds/" + fileName + ".wav");
        
        byte[] data = fileHandle.readBytes();
        this.decoded_buffer = EngineContext.get().AUDIO().decodeBytes(data, 2); // length is decoded automatically
        this.sound = EngineContext.get().AUDIO().createSound(decoded_buffer);
        
        this.sound.setLinkedAudioBuffer(decoded_buffer);
    }
    
    public void play(){
        sound.setSpatialization(false);
        sound.play();
    }
    
    public void play3d(Vector3 audioPosition, float maxRange){
        sound.setSpatialization(true);
        sound.setPosition(audioPosition.x, 
                audioPosition.y, 
                audioPosition.z);
        sound.setAttenuationModel(MAAttenuationModel.LINEAR);
        sound.setDistanceRange(maxRange / 2, maxRange);
        sound.setRolloff(1f);
        sound.play();
    }
    
    public void loop(){
        sound.loop();
    }
}
