package house.x1337.app.smb3.model.game.player.level;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LevelScenePlayerAnimatorSpecifications {
    private final float quadWidth;
    private final float quadHeight;

    @Builder.Default
    private final float rightPadding = 0;
    @Builder.Default
    private final float kickLeadingOffset = 0;

    private final int[] walkFrameSequence;
    private final int[] runFrameSequence;
}
