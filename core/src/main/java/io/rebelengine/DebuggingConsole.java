package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class DebuggingConsole extends InputAdapter implements Disposable {
    private static final float TEXT_X = 200f;
    private static final float TEXT_Y = 300f;
    private static final float TEXT_PADDING_X = 8f;
    private static final float TEXT_PADDING_TOP = 8f;
    private static final float TEXT_PADDING_BOTTOM = 12f;
    private static final float BACKSPACE_REPEAT_DELAY = 0.35f;
    private static final float BACKSPACE_REPEAT_INTERVAL = 0.045f;

    private final SpriteBatch uiBatch;
    private final BitmapFont uiFont;
    private final GlyphLayout glyphLayout;
    private final ShapeRenderer shapeRenderer;
    private final ScreenViewport uiViewport;
    private final StringBuilder inputBuffer;
    private final DebugState debugState;
    private final DebugCommandRegistry commandRegistry;
    private final DebugContext debugContext;
    private final DebugRenderContext debugRenderContext;

    private boolean visible = false;
    private boolean suppressNextTypedCharacter = false;
    private float backspaceHeldTime = 0f;
    private float backspaceRepeatTimer = 0f;

    public DebuggingConsole(RebelEngineBase engine) {
        uiBatch = new SpriteBatch();
        uiFont = new BitmapFont();
        glyphLayout = new GlyphLayout();
        shapeRenderer = new ShapeRenderer();
        uiViewport = new ScreenViewport(new OrthographicCamera());
        inputBuffer = new StringBuilder();
        debugState = new DebugState();
        commandRegistry = new DebugCommandRegistry();
        debugContext = new DebugContext(engine, debugState);
        debugRenderContext = new DebugRenderContext(uiBatch, uiFont, glyphLayout, shapeRenderer, uiViewport);
    }

    public void render() {
        uiViewport.apply();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        commandRegistry.render(debugRenderContext, debugContext);
        renderConsoleLine();
        Gdx.gl.glDepthMask(true);
    }

    public void update(float delta) {
        if (!visible) {
            backspaceHeldTime = 0f;
            backspaceRepeatTimer = 0f;
            return;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.BACKSPACE)) {
            backspaceHeldTime += delta;
            if (backspaceHeldTime >= BACKSPACE_REPEAT_DELAY) {
                backspaceRepeatTimer += delta;
                while (backspaceRepeatTimer >= BACKSPACE_REPEAT_INTERVAL) {
                    deleteLastCharacter();
                    backspaceRepeatTimer -= BACKSPACE_REPEAT_INTERVAL;
                }
            }
        } else {
            backspaceHeldTime = 0f;
            backspaceRepeatTimer = 0f;
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void resize(int width, int height) {
        uiViewport.update(width, height, true);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.SLASH) {
            visible = !visible;
            Gdx.input.setCursorCatched(!visible);
            suppressNextTypedCharacter = true;
            backspaceHeldTime = 0f;
            backspaceRepeatTimer = 0f;
            return true;
        }
        if (!visible) {
            return false;
        }

        if (keycode == Input.Keys.BACKSPACE) {
            deleteLastCharacter();
            backspaceHeldTime = 0f;
            backspaceRepeatTimer = 0f;
            return true;
        }

        if (keycode == Input.Keys.ENTER) {
            submitCommand();
            return true;
        }

        return false;
    }

    private void renderConsoleLine() {
        if (!visible) {
            return;
        }

        String debugText = "Console _ [" + inputBuffer + "]";
        glyphLayout.setText(uiFont, debugText);

        shapeRenderer.setProjectionMatrix(uiViewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.82f);
        shapeRenderer.rect(
            TEXT_X - TEXT_PADDING_X,
            TEXT_Y - glyphLayout.height - TEXT_PADDING_BOTTOM,
            glyphLayout.width + (TEXT_PADDING_X * 2f),
            glyphLayout.height + TEXT_PADDING_TOP + TEXT_PADDING_BOTTOM
        );
        shapeRenderer.end();

        uiBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        uiBatch.begin();
        uiFont.setColor(Color.WHITE);
        uiFont.draw(uiBatch, debugText, TEXT_X, TEXT_Y);
        if (!debugState.lastCommandStatus.isEmpty()) {
            uiFont.setColor(Color.LIGHT_GRAY);
            uiFont.draw(uiBatch, debugState.lastCommandStatus, TEXT_X, TEXT_Y - 24f);
        }
        uiBatch.end();
    }

    public void registerCommand(DebugCommand command) {
        commandRegistry.register(command);
    }

    private void submitCommand() {
        String command = inputBuffer.toString();
        inputBuffer.setLength(0);
        debugState.lastCommandStatus = commandRegistry.execute(command, debugContext);
    }

    @Override
    public boolean keyTyped(char character) {
        if (!visible) {
            return false;
        }
        if (suppressNextTypedCharacter) {
            suppressNextTypedCharacter = false;
            return false;
        }
        if (character == '\r' || character == '\n' || character == '\b') {
            return false;
        }
        if (character >= 32 && character != 127) {
            inputBuffer.append(character);
            return true;
        }
        return false;
    }

    private void deleteLastCharacter() {
        if (inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
        }
    }

    @Override
    public void dispose() {
        uiBatch.dispose();
        uiFont.dispose();
        shapeRenderer.dispose();
    }
}
