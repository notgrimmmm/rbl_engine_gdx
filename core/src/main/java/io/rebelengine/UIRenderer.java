/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Set up fonts and everything relating to user interface here
 */
public class UIRenderer {
    private final SpriteBatch ui_batch;
    private final BitmapFont ui_font;
    
    private final Vector3 tmp = new Vector3();
    private final float visionRadius = 300f;
    
    public UIRenderer(){
        ui_batch = new SpriteBatch();
        
        //custom font
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("font/TEXT-QK.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 12;
        parameter.color = Color.WHITE;
        
        ui_font = generator.generateFont(parameter);
        ui_font.getData().markupEnabled = true;
        generator.dispose();
    }
    
    public void render(Camera camera){
        // get all text ui 
        Array<UI_TextInteraction> txtArray = GameContext.get().get_text_interactions();
        // get all modules ui
        
        //render
        //ui_batch.setProjectionMatrix(camera.combined); // no needed maybe only for some other type of rendering but not this also not this engine camera
        ui_batch.begin();
        for (UI_TextInteraction txtI : txtArray){
            tmp.set(txtI.position); // smooth for text
            //if (tmp.dst(camera.position) > visionRadius) continue; // THIS IS BUGGED> I DONT THINK CAM POS IS CHANGING< AFTER A WHILE TEXT DOESNT WORK AT RANGED
            if (!txtI.visible) continue;
            
            if (txtI.twoDProjectionFromCenter != null){
                ui_font.draw(ui_batch, txtI.text, Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2);
                continue;
            }
            
            Vector3 screenCoords = camera.project(tmp);

            if (Float.isNaN(screenCoords.x) || Float.isNaN(screenCoords.y) || screenCoords.z < 0f) continue;
            if (screenCoords.z < 0f || screenCoords.z > 1f) continue;
            
            
            
            ui_font.draw(ui_batch, txtI.text,screenCoords.x, screenCoords.y);
        }
        ui_batch.end();
    }
}
