package io.rebelengine.Debugging;

import io.rebelengine.Debugging.DebugState;
import io.rebelengine.Debugging.DebugRenderContext;
import io.rebelengine.Debugging.DebugContext;
import io.rebelengine.Debugging.DebugCommandRegistry;
import io.rebelengine.Debugging.DebugCommand;
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
import io.rebelengine.RebelEngineBase;

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
    
    // Add this field alongside your other fields
    private final java.util.List<String> activeSuggestions = new java.util.ArrayList<>();

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
    
    private void updateSuggestions() {
        activeSuggestions.clear();
        String currentInput = inputBuffer.toString().trim().toLowerCase();

        // Don't show suggestions if nothing has been typed yet
        if (currentInput.isEmpty()) {
            return;
        }

        // Loop through your registry and match names that start with the input
        // NOTE: Replace commandRegistry.getCommandNames() with whatever method your registry uses to expose command strings
        for (String commandName : commandRegistry.getCommandNames()) {
            if (commandName.toLowerCase().startsWith(currentInput)) {
                activeSuggestions.add(commandName);
            }
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.SLASH) {
            visible = !visible;
            Gdx.input.setCursorCatched(!visible);
            suppressNextTypedCharacter = true;
            backspaceHeldTime = 0f;
            backspaceRepeatTimer = 0f;
            activeSuggestions.clear(); // Clear when toggled
            return true;
        }

        if (!visible) {
            return false;
        }

        // --- TAB AUTOFILL LOGIC ---
        if (keycode == Input.Keys.TAB) {
            if (!activeSuggestions.isEmpty()) {
                String topMatch = activeSuggestions.get(0);
                inputBuffer.setLength(0);
                inputBuffer.append(topMatch).append(" "); // Fill command and add a trailing space for arguments
                updateSuggestions(); // Re-filter (will now be empty or match args if your registry supports it)
            }
            return true;
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

        // Draw Main Input Background
        shapeRenderer.setColor(0f, 0f, 0f, 0.82f);
        float mainBoxWidth = Math.max(glyphLayout.width + (TEXT_PADDING_X * 2f), 250f);
        float mainBoxHeight = glyphLayout.height + TEXT_PADDING_TOP + TEXT_PADDING_BOTTOM;
        shapeRenderer.rect(
            TEXT_X - TEXT_PADDING_X,
            TEXT_Y - glyphLayout.height - TEXT_PADDING_BOTTOM,
            mainBoxWidth,
            mainBoxHeight
        );

        // --- DRAW SUGGESTIONS BOX IF MATCHES EXIST ---
        float suggestionYOffset = 24f; 
        if (!activeSuggestions.isEmpty()) {
            shapeRenderer.setColor(0.05f, 0.05f, 0.05f, 0.9f); // Slightly darker box for suggestions
            float lineSpacing = 18f;
            float suggestionsBoxHeight = (activeSuggestions.size() * lineSpacing) + 8f;

            shapeRenderer.rect(
                TEXT_X - TEXT_PADDING_X,
                TEXT_Y - glyphLayout.height - TEXT_PADDING_BOTTOM - suggestionsBoxHeight,
                mainBoxWidth,
                suggestionsBoxHeight
            );
        }
        shapeRenderer.end();

        // Render Text Elements
        uiBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        uiBatch.begin();

        // 1. Draw main console input text
        uiFont.setColor(Color.WHITE);
        uiFont.draw(uiBatch, debugText, TEXT_X, TEXT_Y);

        // 2. Draw active matching options below input line
        if (!activeSuggestions.isEmpty()) {
            float currentY = TEXT_Y - 22f;
            for (int i = 0; i < activeSuggestions.size(); i++) {
                String option = activeSuggestions.get(i);
                if (i == 0) {
                    uiFont.setColor(Color.GOLD); // Highlight the dominant autofill target
                    uiFont.draw(uiBatch, "> " + option + " [Tab]", TEXT_X, currentY);
                } else {
                    uiFont.setColor(Color.GRAY);
                    uiFont.draw(uiBatch, "  " + option, TEXT_X, currentY);
                }
                currentY -= 18f;
            }
            // Push the old execution status message down past our suggestion window
            suggestionYOffset = 22f + (activeSuggestions.size() * 18f) + 12f;
        }

        // 3. Draw old command execution feedback status
        if (!debugState.lastCommandStatus.isEmpty()) {
            uiFont.setColor(Color.LIGHT_GRAY);
            uiFont.draw(uiBatch, debugState.lastCommandStatus, TEXT_X, TEXT_Y - suggestionYOffset);
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
        activeSuggestions.clear(); // <-- Clear here
    }

    @Override
    public boolean keyTyped(char character) {
        if (!visible) return false;
        if (suppressNextTypedCharacter) { suppressNextTypedCharacter = false; return false; }
        if (character == '\r' || character == '\n' || character == '\b' || character == '\t') return false; // Ignore tab here too

        if (character >= 32 && character != 127) {
            inputBuffer.append(character);
            updateSuggestions(); // <-- Update here
            return true;
        }
        return false;
    }

    private void deleteLastCharacter() {
        if (inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            updateSuggestions(); // <-- Update here
        }
    }
    
    public boolean get_noclipState(){
        return debugState.noclip;
    }

    @Override
    public void dispose() {
        uiBatch.dispose();
        uiFont.dispose();
        shapeRenderer.dispose();
    }
}
