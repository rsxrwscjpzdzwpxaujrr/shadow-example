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

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Hello {
    private long window;
    private final Random random = new Random();
    private ShadowServer shadowServer;
    private final List<Box> boxes = new ArrayList<>();
    private final int windowWidth = 800;
    private final int windowHeight = 600;

    public void run() {
        init();
        loop();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(windowWidth, windowHeight, "Hello World!", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwSetMouseButtonCallback(window, new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (action == GLFW_PRESS) {
                    int[] cursorPos;

                    switch (button) {
                        case GLFW_MOUSE_BUTTON_RIGHT:
                            cursorPos = getCursorPos();

                            Color color = new Color(
                                    0.75f + (random.nextFloat() - 0.5f) / 2.0f,
                                    0.75f + (random.nextFloat() - 0.5f) / 2.0f,
                                    0.75f + (random.nextFloat() - 0.5f) / 2.0f,
                                    random.nextFloat() / 3.0f
                            );

                            shadowServer.addLight(new SimpleLight(cursorPos[0], cursorPos[1], 600.0, color));
                            break;

                        case GLFW_MOUSE_BUTTON_MIDDLE:
                            cursorPos = getCursorPos();

                            Box box = new Box(cursorPos[0] - 50.0f / 2.0f, cursorPos[1] - 50.0f / 2.0f, 50.0f);

                            boxes.add(box);
                            shadowServer.addClient(box);
                            break;
                    }
                }
            }
        });

        glfwShowWindow(window);
    }

    private void loop() {
        GL.createCapabilities();

        shadowServer = new ShadowServer();

        Box tempBox = new Box(100.0f, 100.0f, 50.0f);
        boxes.add(tempBox);
        shadowServer.addClient(tempBox);

        shadowServer.addLight(new SimpleLight(
                windowWidth / 2.0,
                windowHeight - 100.0,
                windowWidth,
                new Color(1.0f, 1.0f, 1.0f, 0.5f)
        ));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glClearColor(0.25f, 0.25f, 0.25f, 0.0f);
        glOrtho(0, windowWidth, 0, windowHeight, -1, 1);

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT);

            if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
                int[] cursorPos = getCursorPos();

                tempBox.setX((float) cursorPos[0] - tempBox.getSize() / 2.0f);
                tempBox.setY((float) cursorPos[1] - tempBox.getSize() / 2.0f);
            }

            shadowServer.update();
            shadowServer.draw();

            for (Box box: boxes) {
                box.draw();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private int[] getCursorPos() {
        double[] xpos = new double[1];
        double[] ypos = new double[1];

        glfwGetCursorPos(window, xpos, ypos);

        ypos[0] = windowHeight - ypos[0];

        return new int[] { (int) xpos[0], (int) ypos[0] };
    }

    public static void main(String[] args) {
        new Hello().run();
    }
}
