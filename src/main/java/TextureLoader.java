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

import org.lwjgl.BufferUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.net.URL;
import java.nio.*;
import java.util.Hashtable;

import static org.lwjgl.opengl.GL11.*;

public class TextureLoader {
    private ColorModel glAlphaColorModel;
    private ColorModel glColorModel;
    private IntBuffer textureIDBuffer = BufferUtils.createIntBuffer(1);

    public TextureLoader() {
        glAlphaColorModel = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[] { 8, 8, 8, 8 },
                true,
                false,
                ComponentColorModel.TRANSLUCENT,
                DataBuffer.TYPE_BYTE
        );

        glColorModel = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[] { 8, 8, 8, 0 },
                false,
                false,
                ComponentColorModel.OPAQUE,
                DataBuffer.TYPE_BYTE
        );
    }

    public int createTextureID() {
        glGenTextures(textureIDBuffer);
        return textureIDBuffer.get(0);
    }

    public int getTexture(String resourceName,
                          int target,
                          int dstPixelFormat,
                          int minFilter,
                          int magFilter) throws IOException {
        int srcPixelFormat;
        int textureID = createTextureID();

        glBindTexture(target, textureID);

        BufferedImage bufferedImage = loadImage(resourceName);

        if (bufferedImage.getColorModel().hasAlpha())
            srcPixelFormat = GL_RGBA;
        else
            srcPixelFormat = GL_RGB;

        ByteBuffer textureBuffer = convertImageData(bufferedImage);

        if (target == GL_TEXTURE_2D) {
            glTexParameteri(target, GL_TEXTURE_MIN_FILTER, minFilter);
            glTexParameteri(target, GL_TEXTURE_MAG_FILTER, magFilter);

            glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP);
        }

        glTexImage2D(
                target,
                0,
                dstPixelFormat,
                get2Fold(bufferedImage.getWidth()),
                get2Fold(bufferedImage.getHeight()),
                0,
                srcPixelFormat,
                GL_UNSIGNED_BYTE,
                textureBuffer
        );

        return textureID;
    }

    private static int get2Fold(int fold) {
        int ret = 2;

        while (ret < fold)
            ret *= 2;

        return ret;
    }

    private ByteBuffer convertImageData(BufferedImage bufferedImage) {
        ByteBuffer imageBuffer;
        WritableRaster raster;
        BufferedImage texImage;

        int texWidth = 2;
        int texHeight = 2;

        while (texWidth < bufferedImage.getWidth())
            texWidth *= 2;

        while (texHeight < bufferedImage.getHeight())
            texHeight *= 2;

        if (bufferedImage.getColorModel().hasAlpha()) {
            raster = Raster.createInterleavedRaster(
                    DataBuffer.TYPE_BYTE,
                    texWidth,
                    texHeight,
                    4,
                    null
            );

            texImage = new BufferedImage(glAlphaColorModel, raster, false, new Hashtable());
        } else {
            raster = Raster.createInterleavedRaster(
                    DataBuffer.TYPE_BYTE,
                    texWidth,
                    texHeight,
                    3,
                    null
            );

            texImage = new BufferedImage(glColorModel, raster, false, new Hashtable());
        }

        Graphics g = texImage.getGraphics();
        g.setColor(new java.awt.Color(0.0f, 0.0f, 0.0f, 0.0f));
        g.fillRect(0, 0, texWidth, texHeight);
        g.drawImage(bufferedImage, 0, 0, null);

        byte[] data = ((DataBufferByte) texImage.getRaster().getDataBuffer()).getData();

        imageBuffer = ByteBuffer.allocateDirect(data.length);
        imageBuffer.order(ByteOrder.nativeOrder());
        imageBuffer.put(data, 0, data.length);
        imageBuffer.flip();

        return imageBuffer;
    }

    private BufferedImage loadImage(String ref) throws IOException {
        URL url = this.getClass().getResource(ref);

        if (url.getContent() == null)
            throw new IOException();

        Image img = new ImageIcon(url).getImage();

        BufferedImage bufferedImage = new BufferedImage(
                img.getWidth(null),
                img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics g = bufferedImage.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        return bufferedImage;
    }
}