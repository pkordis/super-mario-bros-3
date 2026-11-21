package house.x1337.app.smb3.game.object.level.block.animation.base;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimationSingleTiled;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.FIXED_POINT_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_BRICK_BLOCK_BOUNCE;

@Getter
public abstract class BlockBounceAnimation extends GameObjectAnimationSingleTiled {
    private static final Dimensions BOUNCE_SPRITE_DIMENSIONS = Dimensions.fullTile("AnimatingObjectDimensions");
    private static final int[] BOUNCE_VELOCITY = {
        0,   // 0  — not used
        -64, // 1  — falling: -4 px/frame (the block is back in place; only a rider uses it)
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
    private boolean started = false;
    private boolean expired = false;

    protected BlockBounceAnimation(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        super(
            gameEngine,
            offset
        );
    }
    protected abstract Texture generateSpriteTexture(final GameEngine gameEngine);

    public boolean isSinking() {
        return false;
    }

    /** Whether the block is back in place, with only the tick that ends the bounce left. */
    public boolean isBackInPlace() {
        return started && bumpPosition == 0 && !expired;
    }

    /** Advances the bounce by one tick. See {@code kb/note-block.md}, "Bounce timeline". */
    public final void tick() {
        if (expired) {
            return;
        }
        if (!started) {
            started = true;
            return;
        }
        if (bumpPosition == 0) {
            expired = true;
            return;
        }

        // Apply the velocity looked up on the previous tick; a sinking block runs the curve upside down
        final int upwardVelocity = isSinking() ? -currentVelocity : currentVelocity;
        yOffset += upwardVelocity * FIXED_POINT_TO_GAME_UNITS;
        updatePosition();

        // Look up the velocity for the next tick
        currentVelocity = BOUNCE_VELOCITY[bumpPosition];
        bumpPosition--;
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
