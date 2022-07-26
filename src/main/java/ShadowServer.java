/*
 * Copyright (c) 2016-2020, Мира Странная <rsxrwscjpzdzwpxaujrr@yahoo.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class ShadowServer {
    private final List<IShadowClient> shadowClients = new ArrayList<>();
    private final List<ILight> lights = new ArrayList<>();
    private final Map<ILight, LightData> lightData;

    private int lightGradientTexture;
    private final float lightOversize = 16.0f;
    private final ShaderProgram program = new ShaderProgram();
    private boolean shadersEnabled = true;

    private static final int buffstep = 32;

    private final int vbo;

    private static class LightData {
        float[] shadows;
        int shadowsLength;
        boolean enabled;
        int buffsize = buffstep;

        private LightData() {
            setBuffsize(buffstep, null);

            clear();
        }

        private void clear() {
            shadowsLength = 0;
            enabled = true;
        }

        public void setBuffsize(int buffsize, ShadowServer server) {
            this.buffsize = buffsize;

            float[] oldShadows = shadows;

            shadows = new float[buffsize];

            if (oldShadows != null) {
                System.arraycopy(oldShadows, 0, shadows, 0, shadowsLength);
            }

            if (server != null) {
                server.updateVboSize();
            }
        }
    }

    ShadowServer() {
        try {
            TextureLoader textureLoader = new TextureLoader();
            lightGradientTexture = textureLoader.getTexture(
                    "linearLightGradient.png",
                    GL_TEXTURE_2D,
                    GL_RGBA,
                    GL_LINEAR,
                    GL_LINEAR
            );

            program.loadShader(GL_VERTEX_SHADER, "light.vert");
            program.loadShader(GL_FRAGMENT_SHADER, "light.frag");
        } catch (IOException e) {
            System.err.println("Can not find resources");
            e.printStackTrace();
        }

        lightData = new HashMap<>();

        glLinkProgram(program.id());

        vbo = glGenBuffers();
        updateVboSize();
    }

    private void updateVboSize() {
        long vertices = 0;

        for (LightData data: lightData.values()) {
            vertices += data.buffsize;
        }

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices * Float.BYTES * 2, GL_DYNAMIC_DRAW);
    }

    public void update() {
        for (ILight light: lights) {
            lightData.get(light).clear();
        }

        List<IShadowClient> nearClients = new ArrayList<>();

        for (ILight light: lights) {
            LightData data = lightData.get(light);

            if (!data.enabled)
                continue;

            float srcX = light.x();
            float srcY = light.y();
            float srcSize = light.maxDistance();

            for (IShadowClient client: shadowClients) {
                if (!data.enabled)
                    break;

                float clientSize = client.size();
                float xDelta = Math.abs(client.shadowClientX() - srcX);
                float yDelta = Math.abs(client.shadowClientY() - srcY);

                if (xDelta < srcSize + clientSize && yDelta < srcSize + clientSize) {
                    if (xDelta < clientSize * 1.5f && yDelta < clientSize * 1.5f) {
                        float[] clvtx = client.triangles();

                        // Проверка перекрывает ли треугольник источник света
                        for (int i = 0; i < clvtx.length; i += 6) {
                            float[] triangle = new float[6];

                            System.arraycopy(clvtx, i, triangle, 0, 6);

                            if (checkIfPointInsideTriangle(triangle, srcX, srcY)) {
                                data.enabled = false;
                                break;
                            }
                        }
                    }

                    if (data.enabled)
                        nearClients.add(client);
                }
            }
        }

        for (ILight light: lights) {
            LightData data = lightData.get(light);

            if (!data.enabled)
                continue;

            float srcX = light.x();
            float srcY = light.y();
            float srcSize = light.maxDistance();

            float[] tempShadows = new float[12];

            for (IShadowClient client: nearClients) {
                float[] clvtx = client.triangles();

                int iter = 0;

                for (int i = 0; i < clvtx.length; i += 2) {
                    float tempSrcSize = srcSize * lightOversize;

                    // Какая-то стрёмная математика, получаем проекцию теней на краях квадрата света
                    if (!(clvtx[i] - srcX < clvtx[i + 1] - srcY ||
                            clvtx[i] - srcX > -(clvtx[i + 1] - srcY)) ||
                            (clvtx[i] - srcX < clvtx[i + 1] - srcY &&
                                    clvtx[i] - srcX > -(clvtx[i + 1] - srcY))) {
                        if (srcY < clvtx[i + 1])
                            tempSrcSize = -tempSrcSize;

                        tempShadows[iter] = -tempSrcSize / ((clvtx[i + 1] - srcY) / (clvtx[i] - srcX)) + srcX;
                        tempShadows[iter + 1] = srcY - tempSrcSize;
                    } else {
                        if (srcX > clvtx[i])
                            tempSrcSize = -tempSrcSize;

                        tempShadows[iter] = srcX + tempSrcSize;
                        tempShadows[iter + 1] = tempSrcSize / ((clvtx[i] - srcX) / (clvtx[i + 1] - srcY)) + srcY;
                    }

                    iter += 2;
                }

                // Получаем из проекций теней на краях квадрата треугольники с тенями
                for (int i = 0; i < iter; i += 6) {
                    for (int j = 0; j < 3; j++) {
                        int a = i + (j == 2 ? 0 : ((j + 1) * 2));
                        int b = i + (j * 2);

                        boolean inverted;

                        if (!(clvtx[i] - srcX < clvtx[i + 1] - srcY ||
                                clvtx[i] - srcX > -(clvtx[i + 1] - srcY)) ||
                                (clvtx[i] - srcX < clvtx[i + 1] - srcY &&
                                        clvtx[i] - srcX > -(clvtx[i + 1] - srcY))) {
                            inverted = srcY > clvtx[i + 1];

                            if (tempShadows[inverted ? a : b] > tempShadows[inverted ? b : a]) {
                                continue;
                            }
                        } else {
                            inverted = srcX > clvtx[i];

                            if (tempShadows[(inverted ? b : a) + 1] > tempShadows[(inverted ? a : b) + 1]) {
                                continue;
                            }
                        }

                        if (data.shadowsLength + 8 > data.buffsize) {
                            data.setBuffsize(data.buffsize + 256, this);
                        }

                        data.shadows[data.shadowsLength] = tempShadows[a];
                        data.shadows[data.shadowsLength + 1] = tempShadows[a + 1];

                        data.shadows[data.shadowsLength + 2] = tempShadows[b];
                        data.shadows[data.shadowsLength + 3] = tempShadows[b + 1];

                        data.shadows[data.shadowsLength + 4] = clvtx[b];
                        data.shadows[data.shadowsLength + 5] = clvtx[b + 1];

                        data.shadows[data.shadowsLength + 6] = clvtx[a];
                        data.shadows[data.shadowsLength + 7] = clvtx[a + 1];

                        data.shadowsLength += 8;
                    }
                }
            }
        }
    }

    public void draw() {
        glEnable(GL_STENCIL_TEST);
        glBlendFunc(GL_ONE, GL_ONE);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexPointer(2, GL_FLOAT, 0, 0);
        glEnableClientState(GL_VERTEX_ARRAY);

        List<ILight> enabledLights = new ArrayList<>();

        for (ILight light: lights) {
            if (lightData.get(light).enabled) {
                enabledLights.add(light);
            }
        }

        int first = 0;

        for (int j = 0; j < enabledLights.size(); j += 8) {
            glClear(GL_STENCIL_BUFFER_BIT);

            // Рисуем тени в буфер трафарета

            glStencilOp(GL_REPLACE, GL_KEEP, GL_KEEP);
            glStencilFunc(GL_NEVER, 0xFF, 0xFF);

            for (int i = j; i < enabledLights.size() && i - j < 8; i++) {
                LightData data = lightData.get(enabledLights.get(i));

                glStencilMask(1 << (i - j));

                glBufferSubData(GL_ARRAY_BUFFER, (long) first * 2 * Float.BYTES, data.shadows);
                glDrawArrays(GL_QUADS, first, data.shadowsLength / 2);

                first += data.buffsize;
            }

            if (shadersEnabled) {
                glUseProgram(program.id());
            } else {
                glEnable(GL_TEXTURE_2D);
                glBindTexture(GL_TEXTURE_2D, lightGradientTexture);
            }

            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            glStencilMask(0xFF);

            for (int i = j; i < enabledLights.size() && i - j < 8; i++) {
                ILight light = enabledLights.get(i);

                float srcX = light.x();
                float srcY = light.y();
                float srcSize = light.maxDistance() * lightOversize;

                glStencilFunc(GL_EQUAL, 0, 1 << (i - j));

                if (shadersEnabled) {
                    glUniform2f(glGetUniformLocation(program.id(), "pos"), srcX, srcY);
                }

                // Рисуем свет
                Color color = light.color();
                glColor3f(color.r * color.a, color.g * color.a, color.b * color.a);

                float halfOversize = (lightOversize - 1.0f) / 2.0f;

                glBegin(GL_QUADS);

                glVertex2d(srcX + srcSize, srcY - srcSize);
                glTexCoord2f(halfOversize + 1.0f, -halfOversize);

                glVertex2d(srcX + srcSize, srcY + srcSize);
                glTexCoord2f(halfOversize + 1.0f, halfOversize + 1.0f);

                glVertex2d(srcX - srcSize, srcY + srcSize);
                glTexCoord2f(-halfOversize, halfOversize + 1.0f);

                glVertex2d(srcX - srcSize , srcY - srcSize);
                glTexCoord2f(-halfOversize, -halfOversize);

                glEnd();
            }

            if (shadersEnabled) {
                glUseProgram(0);
            } else {
                glDisable(GL_TEXTURE_2D);
            }
        }

        glDisable(GL_STENCIL_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void addClient(IShadowClient shadowClient) {
        shadowClients.add(shadowClient);
    }

    public void removeClient(IShadowClient shadowClient) {
        shadowClients.remove(shadowClient);
    }

    public void addLight(ILight light) {
        lights.add(light);

        lightData.put(light, new LightData());

        updateVboSize();
    }

    public void removeLight(ILight light) {
        lights.remove(light);

        lightData.remove(light);
    }

    private static float triangleArea(float[] triangle) {
        float x1 = triangle[0];
        float y1 = triangle[1];
        float x2 = triangle[2];
        float y2 = triangle[3];
        float x3 = triangle[4];
        float y3 = triangle[5];

        return Math.abs((x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)) / 2.0f);
    }

    private static boolean checkIfPointInsideTriangle(float[] triangle, float x, float y) {
        float x1 = triangle[0];
        float y1 = triangle[1];
        float x2 = triangle[2];
        float y2 = triangle[3];
        float x3 = triangle[4];
        float y3 = triangle[5];

        float A = triangleArea(triangle);

        float A1 = triangleArea(new float[] { x, y, x2, y2, x3, y3 });
        float A2 = triangleArea(new float[] { x1, y1, x, y, x3, y3 });
        float A3 = triangleArea(new float[] { x1, y1, x2, y2, x, y });

        return (Math.abs(A - (A1 + A2 + A3)) < 0.0125f);
    }

    public boolean shadersEnabled() {
        return shadersEnabled;
    }

    public void setShadersEnabled(boolean shadersEnabled) {
        this.shadersEnabled = shadersEnabled;

        if (!shadersEnabled)
            glUseProgram(0);
    }
}
