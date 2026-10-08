package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

public class NavMeshDebugRenderer implements Disposable {

    public Color fillColor = new Color(0.4f, 0.4f, 0.4f, 0.7f); // Default semi-transparent grey fill
    public Color outlineColor = new Color(0.1f, 0.1f, 0.1f, 1.0f); // Default dark grey outline

    private Mesh fillMesh;
    private Mesh lineMesh;
    private ShaderProgram shader;

    public NavMeshDebugRenderer(NavMeshExtractor.NavMeshGeometry navGeometry) {
        if (navGeometry == null || navGeometry.allVertices == null || navGeometry.indices == null) {
            return;
        }

        int totalVerts = navGeometry.allVertices.size;
        int totalTriangleIndices = navGeometry.indices.size;

        if (totalVerts == 0 || totalTriangleIndices == 0) {
            return;
        }

        // Flatten Array<Vector3> into packed float array [x, y, z, ...]
        float[] floatVertices = new float[totalVerts * 3];
        for (int i = 0; i < totalVerts; i++) {
            Vector3 v = navGeometry.allVertices.get(i);
            floatVertices[i * 3]     = v.x;
            floatVertices[i * 3 + 1] = v.y;
            floatVertices[i * 3 + 2] = v.z;
        }

        // Direct indices for solid triangles
        short[] triangleIndices = new short[totalTriangleIndices];
        for (int i = 0; i < totalTriangleIndices; i++) {
            triangleIndices[i] = navGeometry.indices.get(i);
        }

        // Expand triangle indices into 3 line segments (6 indices per triangle) for the outline
        int lineIndexCount = (totalTriangleIndices / 3) * 6;
        short[] lineIndices = new short[lineIndexCount];

        int writeIdx = 0;
        for (int i = 0; i < totalTriangleIndices; i += 3) {
            short i1 = navGeometry.indices.get(i);
            short i2 = navGeometry.indices.get(i + 1);
            short i3 = navGeometry.indices.get(i + 2);

            // Edge 1: V1 -> V2
            lineIndices[writeIdx++] = i1;
            lineIndices[writeIdx++] = i2;

            // Edge 2: V2 -> V3
            lineIndices[writeIdx++] = i2;
            lineIndices[writeIdx++] = i3;

            // Edge 3: V3 -> V1
            lineIndices[writeIdx++] = i3;
            lineIndices[writeIdx++] = i1;
        }

        // Create meshes
        fillMesh = new Mesh(
            true, totalVerts, totalTriangleIndices,
            new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE)
        );
        fillMesh.setVertices(floatVertices);
        fillMesh.setIndices(triangleIndices);

        lineMesh = new Mesh(
            true, totalVerts, lineIndexCount,
            new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE)
        );
        lineMesh.setVertices(floatVertices);
        lineMesh.setIndices(lineIndices);

        // Passthrough Shader
        String vertexShader = 
            "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" +
            "uniform mat4 u_projModelView;\n" +
            "void main() {\n" +
            "   gl_Position = u_projModelView * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" +
            "}\n";

        String fragmentShader = 
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform vec4 u_color;\n" +
            "void main() {\n" +
            "   gl_FragColor = u_color;\n" +
            "}\n";

        shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            throw new IllegalArgumentException("NavMesh debug shader compile error: " + shader.getLog());
        }
    }

    public void render(Camera camera) {
        render(camera, fillColor, outlineColor);
    }

    public void render(Camera camera, Color fill, Color outline) {
        if (fillMesh == null || lineMesh == null || shader == null) return;

        shader.bind();
        shader.setUniformMatrix("u_projModelView", camera.combined);

        // Enable blending for transparency support
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Render Solid Triangles (with polygon offset to pull fill back in Z-buffer)
        Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
        Gdx.gl.glPolygonOffset(1.0f, 1.0f);

        shader.setUniformf("u_color", fill.r, fill.g, fill.b, fill.a);
        fillMesh.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);

        // Render Wireframe Outlines over the fill
        shader.setUniformf("u_color", outline.r, outline.g, outline.b, outline.a);
        lineMesh.render(shader, GL20.GL_LINES);

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        if (fillMesh != null) {
            fillMesh.dispose();
            fillMesh = null;
        }
        if (lineMesh != null) {
            lineMesh.dispose();
            lineMesh = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }
}