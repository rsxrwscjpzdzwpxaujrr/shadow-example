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
    private static final int buffSize = 8192 * 12;
    private final float lightOversize = 16.0f;
    private final ShaderProgram program = new ShaderProgram();
    private boolean shadersEnabled = true;

    private static class LightData {
        float[] shadows;
        int shadowsLength;
        boolean enabled;

        private LightData() {
            shadows = new float[buffSize];

            clear();
        }

        private void clear() {
            shadowsLength = 0;
            enabled = true;
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

            for (IShadowClient client: nearClients) {
                float[] clvtx = client.triangles();
                float[] tempShadows = new float[4];
                int iter = 0;

                for (int i = 0; i < clvtx.length; i += 2) {
                    float tempSrcSize = srcSize * lightOversize;

                    // Какая-то стрёмная математика, получаем проекцию теней на краях квадрата света
                    if (  !(clvtx[i] - srcX <   clvtx[i + 1] - srcY   ||
                            clvtx[i] - srcX > -(clvtx[i + 1] - srcY)) ||
                           (clvtx[i] - srcX <   clvtx[i + 1] - srcY   &&
                            clvtx[i] - srcX > -(clvtx[i + 1] - srcY))) {
                        if (srcY < clvtx[i + 1])
                            tempSrcSize = -tempSrcSize;

                        tempShadows[iter] = -tempSrcSize / ((clvtx[i + 1] - srcY) / (clvtx[i] - srcX))  + srcX;
                        tempShadows[iter + 1] = srcY - tempSrcSize;
                    } else {
                        if (srcX > clvtx[i])
                            tempSrcSize = -tempSrcSize;

                        tempShadows[iter] = srcX + tempSrcSize;
                        tempShadows[iter + 1] = tempSrcSize / ((clvtx[i] - srcX) / (clvtx[i + 1] - srcY)) + srcY;
                    }

                    iter += 2;

                    // Получаем из проекций теней на краях квадрата треугольники с тенями
                    if (i > 0 && iter % 4 == 0) {
                        int temp = iter - 4;
                        int tempi = (i + 2) - 4;

                        data.shadows[data.shadowsLength]      = tempShadows[temp];
                        data.shadows[data.shadowsLength + 1]  = tempShadows[temp + 1];

                        data.shadows[data.shadowsLength + 2]  = tempShadows[temp + 2];
                        data.shadows[data.shadowsLength + 3]  = tempShadows[temp + 3];

                        data.shadows[data.shadowsLength + 4]  = clvtx[tempi];
                        data.shadows[data.shadowsLength + 5]  = clvtx[tempi + 1];

                        data.shadows[data.shadowsLength + 6]  = tempShadows[temp + 2];
                        data.shadows[data.shadowsLength + 7]  = tempShadows[temp + 3];

                        data.shadows[data.shadowsLength + 8]  = clvtx[tempi + 2];
                        data.shadows[data.shadowsLength + 9]  = clvtx[tempi + 3];

                        data.shadows[data.shadowsLength + 10] = clvtx[tempi];
                        data.shadows[data.shadowsLength + 11] = clvtx[tempi + 1];

                        data.shadowsLength += 12;

                        iter = 0;
                    }
                }
            }
        }
    }

    public void draw() {
        glEnable(GL_STENCIL_TEST);
        glBlendFunc(GL_ONE, GL_ONE);

        for (ILight light: lights) {
            LightData data = lightData.get(light);

            if (!data.enabled)
                continue;

            float srcX = light.x();
            float srcY = light.y();
            float srcSize = light.maxDistance() * lightOversize;

            glClear(GL_STENCIL_BUFFER_BIT);

            glStencilFunc(GL_NEVER, 1, 0);
            glStencilOp(GL_REPLACE, GL_KEEP, GL_KEEP);

            // Рисуем тени в буфер трафарета
            glColor3f(1.0f, 1.0f, 1.0f);

            glBegin(GL_TRIANGLES);

            for (int i = 0; i < data.shadowsLength; i += 2)
                glVertex2f(data.shadows[i], data.shadows[i + 1]);

            glEnd();

            glStencilFunc(GL_NOTEQUAL, 1, 1);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

            // Рисуем свет
            Color color = light.color();
            glColor3f(color.r * color.a, color.g * color.a, color.b * color.a);

            if (shadersEnabled) {
                glUseProgram(program.id());
                glUniform2f(glGetUniformLocation(program.id(), "pos"), srcX, srcY);
            } else {
                glEnable(GL_TEXTURE_2D);
                glBindTexture(GL_TEXTURE_2D, lightGradientTexture);
            }

            float halfOversize = (lightOversize - 1.0f) / 2.0f;

            glBegin(GL_QUADS);
            glVertex2d(srcX - srcSize , srcY - srcSize);
            glTexCoord2f(-halfOversize, -halfOversize);

            glVertex2d(srcX - srcSize, srcY + srcSize);
            glTexCoord2f(-halfOversize, halfOversize + 1.0f);

            glVertex2d(srcX + srcSize, srcY + srcSize);
            glTexCoord2f(halfOversize + 1.0f, halfOversize + 1.0f);

            glVertex2d(srcX + srcSize, srcY - srcSize);
            glTexCoord2f(halfOversize + 1.0f, -halfOversize);
            glEnd();

            if (shadersEnabled)
                glUseProgram(0);
            else
                glDisable(GL_TEXTURE_2D);
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
