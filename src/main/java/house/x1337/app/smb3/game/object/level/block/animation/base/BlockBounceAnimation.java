package house.x1337.app.smb3.game.object.level.block.animation.base;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimationSingleTiled;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.FIXED_POINT_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_BRICK_BLOCK_BOUNCE;

@Getter
public abstract class BlockBounceAnimation extends GameObjectAnimationSingleTiled {
    private static final Dimensions BOUNCE_SPRITE_DIMENSIONS = Dimensions.fullTile("AnimatingObjectDimensions");
    private static final int[] BOUNCE_VELOCITY = {
        0,   // 0  — not used (object destroyed before this frame)
        -64, // 1  — falling: -4 px/frame
        -64, // 2  — falling: -4 px/frame
        -48, // 3  — falling: -3 px/frame
        -32, // 4  — falling: -2 px/frame
        -16, // 5  — falling: -1 px/frame
        0,   // 6  — apex: 0
        16,  // 7  — rising: +1 px/frame
        32,  // 8  — rising: +2 px/frame
        48,  // 9  — rising: +3 px/frame
        64   // 10 — rising: +4 px/frame
    };
    private static final int INITIAL_BUMP_POSITION = 10;

    private double yOffset = 0.0;
    private int bumpPosition = INITIAL_BUMP_POSITION;
    private int currentVelocity = 0;
    private boolean expired = false;

    protected BlockBounceAnimation(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        super(
            gameEngine,
            offset
        );
    }
    protected abstract Texture generateSpriteTexture(final GameEngine gameEngine);

    public final void tick() {
        if (expired) {
            return;
        }

        // 1. Apply CURRENT velocity (set by previous frame's lookup)
        yOffset += currentVelocity * FIXED_POINT_TO_GAME_UNITS;

        // 2. Update sprite position
        updatePosition();

        // 3. Look up NEW velocity from table for NEXT frame
        currentVelocity = BOUNCE_VELOCITY[bumpPosition];

        // 4. Decrement position counter
        bumpPosition--;

        // 5. Check for completion (at pos=0, object is destroyed before next tick)
        if (bumpPosition <= 0) {
            expired = true;
        }
    }

    @Override
    public final Geometry generateSpriteGeometry(final GameEngine gameEngine) {
        return fromTexture(
            gameEngine.getAssetManager(),
            generateSpriteTexture(gameEngine),
            BOUNCE_SPRITE_DIMENSIONS
        );
    }

    @Override
    public final WorldOffset calculateTickedWorldOffset(
        final Geometry spriteGeometry,
        final WorldOffset currentWorldOffset
    ) {
        return WorldOffset.of(
            currentWorldOffset.x(),
            (float) (currentWorldOffset.y() + yOffset),
            Z_DEPTH_BRICK_BLOCK_BOUNCE
        );
    }
}
