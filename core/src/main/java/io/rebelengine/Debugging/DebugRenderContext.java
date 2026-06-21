package io.rebelengine.Debugging;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class DebugRenderContext {
    private final SpriteBatch uiBatch;
    private final BitmapFont uiFont;
    private final GlyphLayout glyphLayout;
    private final ShapeRenderer shapeRenderer;
    private final ScreenViewport uiViewport;

    public DebugRenderContext(SpriteBatch uiBatch, BitmapFont uiFont, GlyphLayout glyphLayout,
                              ShapeRenderer shapeRenderer, ScreenViewport uiViewport) {
        this.uiBatch = uiBatch;
        this.uiFont = uiFont;
        this.glyphLayout = glyphLayout;
        this.shapeRenderer = shapeRenderer;
        this.uiViewport = uiViewport;
    }

    public SpriteBatch uiBatch() {
        return uiBatch;
    }

    public BitmapFont uiFont() {
        return uiFont;
    }

    public GlyphLayout glyphLayout() {
        return glyphLayout;
    }

    public ShapeRenderer shapeRenderer() {
        return shapeRenderer;
    }

    public ScreenViewport uiViewport() {
        return uiViewport;
    }
}
