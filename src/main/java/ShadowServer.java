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
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class ShadowServer {
    private final ArrayList<IShadowClient> shadowClients = new ArrayList<>();
    private final ArrayList<ILight> lights = new ArrayList<>();
    private final Map<ILight, LightData> lightData;

    private int lightGradientTexture;
    private static final int buffSize = 1024 * 12;
    private final float lightOversize = 16.0f;

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
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        lightData = new HashMap<>();
    }

    public void update() {
        Map<ILight, Integer> verticesCount = new HashMap<>();

        for (ILight light: lights) {
            lightData.get(light).clear();
        }

        for (ILight light: lights) {
            float srcX = (float) light.getX();
            float srcY = (float) light.getY();
            float srcSize = (float) light.getMaxDistance();

            float[] tempShadows = new float[buffSize];
            float[] tempVertices = new float[buffSize];

            float tempSrcSize;
            int iter = 0;
            
            LightData data = lightData.get(light);

            for (IShadowClient client: shadowClients) {
                if (!data.enabled)
                    break;

                if (    Math.abs(client.getShadowClientX() - srcX) < srcSize + client.getSize() &&
                        Math.abs(client.getShadowClientY() - srcY) < srcSize + client.getSize()) {
                    float[] clvtx = client.getTriangles();

                    for (int i = 0; i < clvtx.length; i += 2) {
                        // Проверка перекрывает ли треугольник источник света
                        if ((clvtx.length - i) % 6 == 0) {
                            float[] triangle = new float[6];

                            for (int j = 0; j < 6; j++) {
                                triangle[j] = clvtx[i + j];
                            }

                            if (checkIfPointInsideTriangle(triangle, srcX, srcY)) {
                                data.enabled = false;
                                break;
                            }
                        }

                        tempSrcSize = srcSize * lightOversize;

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

                        tempVertices[iter] = clvtx[i];
                        tempVertices[iter + 1] = clvtx[i + 1];

                        iter += 2;

                        // Получаем из проекций теней на краях квадрата треугольники с тенями
                        if (iter % 4 == 0) {
                            int temp = iter - 4;

                            data.shadows[data.shadowsLength]      = tempShadows[temp];
                            data.shadows[data.shadowsLength + 1]  = tempShadows[temp + 1];

                            data.shadows[data.shadowsLength + 2]  = tempShadows[temp + 2];
                            data.shadows[data.shadowsLength + 3]  = tempShadows[temp + 3];

                            data.shadows[data.shadowsLength + 4]  = tempVertices[temp];
                            data.shadows[data.shadowsLength + 5]  = tempVertices[temp + 1];

                            data.shadows[data.shadowsLength + 6]  = tempShadows[temp + 2];
                            data.shadows[data.shadowsLength + 7]  = tempShadows[temp + 3];

                            data.shadows[data.shadowsLength + 8]  = tempVertices[temp + 2];
                            data.shadows[data.shadowsLength + 9]  = tempVertices[temp + 3];

                            data.shadows[data.shadowsLength + 10] = tempVertices[temp];
                            data.shadows[data.shadowsLength + 11] = tempVertices[temp + 1];

                            data.shadowsLength += 12;
                        }
                    }

                    verticesCount.put(light, iter);
                }
            }
        }
    }

    public void draw() {
        glEnable(GL_STENCIL_TEST);
        glBlendFunc(GL_DST_COLOR, GL_ONE);

        for (ILight light: lights) {
            LightData data = lightData.get(light);

            if (!data.enabled)
                continue;

            glClear(GL_STENCIL_BUFFER_BIT);

            double srcSize = light.getMaxDistance() * lightOversize;

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
            Color color = light.getColor();
            glColor3f(color.r * color.a, color.g * color.a, color.b * color.a);

            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, lightGradientTexture);

            float halfOversize = (lightOversize - 1.0f) / 2.0f;

            glBegin(GL_QUADS);
            glVertex2d(light.getX() - srcSize , light.getY() - srcSize);
            glTexCoord2f(-halfOversize, -halfOversize);

            glVertex2d(light.getX() - srcSize, light.getY() + srcSize);
            glTexCoord2f(-halfOversize, halfOversize + 1.0f);

            glVertex2d(light.getX() + srcSize, light.getY() + srcSize);
            glTexCoord2f(halfOversize + 1.0f, halfOversize + 1.0f);

            glVertex2d(light.getX() + srcSize, light.getY() - srcSize);
            glTexCoord2f(halfOversize + 1.0f, -halfOversize);
            glEnd();

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

        double A = triangleArea(triangle);

        double A1 = triangleArea(new float[] { x, y, x2, y2, x3, y3 });
        double A2 = triangleArea(new float[] { x1, y1, x, y, x3, y3 });
        double A3 = triangleArea(new float[] { x1, y1, x2, y2, x, y });

        return (A == A1 + A2 + A3);
    }
}
