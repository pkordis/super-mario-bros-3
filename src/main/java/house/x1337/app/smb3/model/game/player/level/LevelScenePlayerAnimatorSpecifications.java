package house.x1337.app.smb3.model.game.player.level;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LevelScenePlayerAnimatorSpecifications {
    private final float quadWidth;
    private final float quadHeight;

    /**
     * Empty space to the right of the body within the sprite canvas (in game
     * units). Sprites are authored facing left with the body flush to the left
     * edge; when the sprite is flipped to face right, the quad is shifted by
     * this amount so the body stays aligned with the collision box regardless
     * of facing. For raccoon/tanooki this space is the tail overflow; for a
     * left-anchored big-Mario frame it is the transparent right margin. Zero
     * when the body fills the full canvas width.
     */
    @Builder.Default
    private final float rightPadding = 0;
    private final int[] walkFrameSequence;
    private final int[] runFrameSequence;
}
