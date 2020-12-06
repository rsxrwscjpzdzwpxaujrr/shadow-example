import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    private int id;

    public ShaderProgram() {
        id = glCreateProgram();
    }

    public void loadShader(int mode, String filename) throws IOException {
        StringBuilder string = new StringBuilder();

        List<String> lines = null;

        try {
            lines = Files.readAllLines(Paths.get(this.getClass().getResource(filename).toURI()), StandardCharsets.UTF_8);
        } catch (NullPointerException e) {
            throw new IOException();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        for (String line : lines)
            string.append(line).append('\n');

        int shaderId = glCreateShader(mode);

        glShaderSource(shaderId, string.toString());
        glCompileShader(shaderId);
        System.out.println(glGetShaderInfoLog(shaderId));

        glAttachShader(id, shaderId);
    }

    public int id() {
        return id;
    }
}
