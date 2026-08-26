package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Score;
import house.x1337.app.smb3.enumeration.resource.RewardImageResource;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.deceleratingRise;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileHeight;

/**
 * Animates the score caption that rises where a coin reward ended.
 *
 * <h2>Motion — ported from dasm {@code prg007.asm Score_GiveAndDraw}</h2>
 * <p>{@code Scores_Counter} starts at $30 (48 ticks) and counts down; the caption rises one
 * pixel whenever the frame counter clears the mask in
 * {@code Score_RiseCounterMask = $03, $01, $00, $00}, selected by the remaining counter
 * divided by 16. The effect is a rise of 1 px/tick that halves every 16 ticks.
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class ScorePopupAnimation implements PopAnimation {
    private static final Dimensions SCORE_CAPTION_DIMENSIONS = halfTileHeight("ScorePopupAnimation");
    private static final PopMotion MOTION = deceleratingRise(16, 3);

    @Value("house.x1337.app.smb3.enumeration.resource.RewardImageResource")
    private EnumeratedImageResource<RewardImageResource> rewardImages;

    private final GameEngine gameEngine;
    private final Score.Data scoreData;
    private final Offset offset;
    private final WorldOffset initialWorldOffset;

    @Setter
    private Geometry spriteGeometry;
    @Setter
    private int frameIndex = 0;
    @Setter
    private boolean expired = false;

    @PostConstruct
    void init() {
        this.spriteGeometry = createAndAttachSprite(
            rewardImages.getTextureFor(scoreData.getImageResource()),
            SCORE_CAPTION_DIMENSIONS
        );
        positionSprite();
    }

    @Override
    public Dimensions getDimensions() {
        return SCORE_CAPTION_DIMENSIONS;
    }

    @Override
    public PopMotion getMotion() {
        return MOTION;
    }
}
