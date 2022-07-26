/*
 * Copyright (c) 2020, Мира Странная <rsxrwscjpzdzwpxaujrr@yahoo.com>
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

import static org.lwjgl.opengl.GL11.*;

public class Box implements IShadowClient {
    private float x;
    private float y;
    private float size;

    public Box(float x, float y, float size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    @Override
    public float[] triangles() {
        return new float[] {
            x + size, y + size,
            x, y + size,
            x, y,
            x + size, y + size,
            x, y,
            x + size, y,
        };
    }

    @Override
    public float shadowClientX() {
        return x();
    }

    @Override
    public float shadowClientY() {
        return y();
    }

    public void draw() {
        float[] vertices = triangles();

        if (vertices.length % 6 != 0)
            return;

        glColor3f(0.0f, 0.0f, 0.0f);

        glBegin(GL_TRIANGLES);

        for (int i = 0; i < vertices.length; i += 2) {
            glVertex2f(vertices[i], vertices[i + 1]);
        }

        glEnd();
    }

    public float x() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float y() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    @Override
    public float size() {
        return size;
    }

    public void setSize(float size) {
        this.size = size;
    }
}
