package house.x1337.app.smb3.model.game;

import com.jme3.scene.shape.Quad;

public record Dimensions(
    String name,
    float width,
    float height
) {
    public Quad toQuad() {
        return new Quad(width, height);
    }
}
