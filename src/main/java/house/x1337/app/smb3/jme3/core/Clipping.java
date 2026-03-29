package house.x1337.app.smb3.jme3.core;

import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import lombok.Data;

@Data
public class Clipping {
    private Vector2f minimum;
    private Vector2f maximum = new Vector2f(0.0F, 0.0F);
    private Vector2f offset = new Vector2f(0.0F, 0.0F);

    public Vector2f clamp(float x, float y) {
        return isClipping() ? new Vector2f(
                FastMath.clamp(x + offset.x, minimum.x, maximum.x),
                FastMath.clamp(y + offset.y, minimum.y, maximum.y)
            ) :
            new Vector2f(x + this.offset.x, y + this.offset.y
        );
    }

    public boolean isClipping() {
        return minimum != null && maximum != null;
    }
}

