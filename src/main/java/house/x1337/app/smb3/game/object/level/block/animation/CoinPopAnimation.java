package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Score;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.game.motion.pop.PopMotions.parabolic;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;
import static house.x1337.app.smb3.model.game.WorldOffset.of;

/**
 * Animates a single-coin reward: the coin pops out above the block, arcs upwards and is
 * removed mid-descent, at which point the score popup takes over.
 *
 * <h2>Physics — ported from dasm {@code prg007.asm CoinPUp_UpdateAndDraw}</h2>
 * <p>{@code CoinPUp_Y += CoinPUp_YVel} every tick; {@code YVel} starts at -5 px/tick and is
 * incremented whenever {@code CoinPUp_Counter} (1 at spawn, incremented before the move)
 * becomes a multiple of 4. {@code PRG007_AE4A} clears {@code CoinPUp_State} once {@code YVel}
 * reaches +5, so the coin is destroyed while still above its spawn point rather than falling
 * back to the block — that is where the score popup appears. The 4-frame spin is the
 * {@code CoinPUp_Patterns} lookup {@code (counter >> 2) & 3}.
 *
 * <h2>Calibration</h2>
 * <p>Offsets are measured in sprite pixels above the block's bottom edge (16 = flush with the
 * block's top edge), and the constants below are calibrated to the curve this project has
 * always rendered: spawn 11, apex 62 at ticks 19-21, 26 at removal, 38 ticks. Rise speed is
 * {@code 647/128} = 5.055 px/tick and gravity {@code 32/128} = 0.25 px/tick².
 *
 * <p>The ROM's own whole-pixel velocity steps trace a slightly taller arc — apex 55 px above
 * spawn over 39 ticks, ending 15 px above spawn — and it spawns the coin 8 px above the
 * block's top edge ({@code prg000.asm}: {@code Y - 24}). The calibrated curve keeps the same
 * net rise-to-removal shape while matching the project's established positions.
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class CoinPopAnimation implements PopAnimation {
    private static final Dimensions COIN_DIMENSIONS = halfTileWidth("CoinPopAnimation");
    private static final PopMotion MOTION = parabolic(11, 647, 32, 128, 38).spinning(4, 4, 2);

    @Value("classpath:/sprites/object/coin/popping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    private final GameEngine gameEngine;
    private final Score.Data scoreData;
    private final Offset offset;

    @Setter
    private Geometry spriteGeometry;
    @Setter
    private int frameIndex = 0;
    @Setter
    private boolean expired = false;
    private WorldOffset initialWorldOffset;
    private int currentTextureIndex = MOTION.textureIndexAt(0);

    @PostConstruct
    void init() {
        initialWorldOffset = of(
            offset.x() + COIN_DIMENSIONS.width() - COIN_DIMENSIONS.width() / 2,
            getLevelScene().getDimensions().rows() - 1 - offset.y() + COIN_DIMENSIONS.height(),
            Z_DEPTH
        );

        spriteGeometry = createAndAttachSprite(animationFrames.getFrame(currentTextureIndex), COIN_DIMENSIONS);
        positionSprite();
    }

    @Override
    public void onFrameAdvanced() {
        final int newTextureIndex = MOTION.textureIndexAt(frameIndex);
        if (newTextureIndex != currentTextureIndex) {
            currentTextureIndex = newTextureIndex;
            spriteGeometry.getMaterial().setTexture("ColorMap", animationFrames.getFrame(currentTextureIndex));
        }
    }

    @Override
    public Dimensions getDimensions() {
        return COIN_DIMENSIONS;
    }

    @Override
    public PopMotion getMotion() {
        return MOTION;
    }
}
