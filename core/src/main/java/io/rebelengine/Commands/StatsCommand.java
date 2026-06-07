package io.rebelengine.Commands;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import io.rebelengine.DebugCommand;
import io.rebelengine.DebugContext;
import io.rebelengine.DebugRenderContext;

public class StatsCommand implements DebugCommand {
    private static final float STATS_X = 100f;
    private static final float STATS_Y_OFFSET = 50f;
    private static final float PADDING_X = 8f;
    private static final float PADDING_TOP = 8f;
    private static final float PADDING_BOTTOM = 12f;

    @Override
    public String key() {
        return "stats";
    }

    @Override
    public String run(String[] args, DebugContext context) {
        context.debugState().showStats = !context.debugState().showStats;
        return context.debugState().showStats ? "stats enabled" : "stats disabled";
    }

    @Override
    public void render(DebugRenderContext renderContext, DebugContext context) {
        if (!context.debugState().showStats) {
            return;
        }

        String statsText = "FPS: " + Gdx.graphics.getFramesPerSecond();
        renderContext.glyphLayout().setText(renderContext.uiFont(), statsText);

        float statsY = renderContext.uiViewport().getScreenHeight() - STATS_Y_OFFSET;

        renderContext.shapeRenderer().setProjectionMatrix(renderContext.uiViewport().getCamera().combined);
        renderContext.shapeRenderer().begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        renderContext.shapeRenderer().setColor(0f, 0f, 0f, 0.72f);
        renderContext.shapeRenderer().rect(
            STATS_X - PADDING_X,
            statsY - renderContext.glyphLayout().height - PADDING_BOTTOM,
            renderContext.glyphLayout().width + (PADDING_X * 2f),
            renderContext.glyphLayout().height + PADDING_TOP + PADDING_BOTTOM
        );
        renderContext.shapeRenderer().end();

        renderContext.uiBatch().setProjectionMatrix(renderContext.uiViewport().getCamera().combined);
        renderContext.uiBatch().begin();
        renderContext.uiFont().setColor(Color.WHITE);
        renderContext.uiFont().draw(renderContext.uiBatch(), statsText, STATS_X, statsY);
        renderContext.uiBatch().end();
    }
}
