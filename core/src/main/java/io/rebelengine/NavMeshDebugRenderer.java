package io.rebelengine;

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

    private Mesh mesh;
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

        // 1. Flatten Array<Vector3> into a packed float array [x, y, z, x, y, z, ...]
        float[] floatVertices = new float[totalVerts * 3];
        for (int i = 0; i < totalVerts; i++) {
            Vector3 v = navGeometry.allVertices.get(i);
            floatVertices[i * 3]     = v.x;
            floatVertices[i * 3 + 1] = v.y;
            floatVertices[i * 3 + 2] = v.z;
        }

        // 2. Convert triangle indices (3 per triangle) to line indices (6 per triangle) for GL_LINES wireframe
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

        // 3. Create static mesh
        mesh = new Mesh(
            true, 
            totalVerts, 
            lineIndexCount, 
            new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE)
        );

        mesh.setVertices(floatVertices);
        mesh.setIndices(lineIndices);

        // 4. Passthrough Shader
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
        render(camera, Color.GREEN);
    }

    public void render(Camera camera, Color color) {
        if (mesh == null || shader == null) return;

        shader.bind();
        shader.setUniformMatrix("u_projModelView", camera.combined);
        shader.setUniformf("u_color", color.r, color.g, color.b, color.a);

        mesh.render(shader, GL20.GL_LINES);
    }

    @Override
    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }
}