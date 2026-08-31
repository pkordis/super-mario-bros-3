package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Score;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SUPER_MUSHROOM;
import static house.x1337.app.smb3.enumeration.Score.SCORE_1000;
import static house.x1337.app.smb3.model.game.WorldOffset.of;
import static java.lang.Math.clamp;
import static java.lang.Math.floor;
import static java.lang.Math.min;

/**
 * A Super Mushroom power-up.
 *
 * <h2>Motion — ported from dasm {@code prg001.asm}</h2>
 * <p>Unlike the {@link SuperLeaf} (which pops up ballistically then flutters and ignores the
 * world), the mushroom follows the {@code ObjInit_PUpMush} / {@code ObjNorm_PUpMush} contract and
 * is a genuine <em>ground-walking</em> item. It has two phases:
 *
 * <ol>
 *   <li><b>Rise ({@code PowerUp_DoRaise} @ PRG001_A8C6).</b> The object's timer starts at
 *       {@code $3d} (61 frames). For the first {@code $3d..$2d} (16) frames nothing happens — the
 *       bumped block is still animating and hides the item. Below {@code $2d} the mushroom creeps
 *       up exactly 1 px every 3 frames (dasm decrements {@code Objects_Var1} from 2 and nudges
 *       {@code Objects_Y} by −1 on underflow), emerging roughly one tile over the remaining frames.
 *       It cannot be collected until its collect-protection timer ({@code Objects_Timer2 = $10})
 *       expires — {@code PowerUp_DoHitTest} @ PRG001_A88C.</li>
 *   <li><b>Move ({@code ObjNorm_PUpMush} @ PRG001_A735).</b> Gravity ({@code OBJECT_FALLRATE = $03}
 *       per frame, capped at {@code OBJECT_MAXFALL = $40}) pulls it onto the block it emerged from;
 *       once grounded with no horizontal velocity, {@code PowerUp_BounceXVel} kicks it rolling at a
 *       constant {@code $10} (1 px/frame) <em>away</em> from the player (fall direction chosen by
 *       {@code Mushroom_SetFall} @ PRG001_A94A). {@code Object_InteractWithWorld} then walks it
 *       across the static tile grid: it rests on solid floors and reverses direction
 *       ({@code Object_AboutFace}) when it runs into a wall.</li>
 * </ol>
 *
 * <p>All velocities are 4.4 fixed-point ({@code value/16 == px/frame}). World collision is resolved
 * directly against the scene's {@link StaticEnvironmentCollisionGrid} — the same tile-aligned solids
 * the player uses — which is why the mushroom, alone among the reward items so far, needs a
 * reference to it. ({@code ObjHit_PUpMush} also grows the player / grants the Super suit; that suit
 * change is still deferred, exactly as the leaf's Raccoon grant is.)
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class SuperMushroom implements RewardLevelObject {
    private static final int RISE_TIMER_INITIAL = 61;
    private static final int RISE_MOVEMENT_THRESHOLD = 45;
    private static final int RISE_PIXEL_INTERVAL_FRAMES = 3;
    private static final int COLLECT_PROTECTION_FRAMES = 16;
    private static final int X_MOVE_SPEED_FIXED_POINT = 16;
    private static final int GRAVITY_FIXED_POINT = 3;
    private static final int MAX_FALL_FIXED_POINT = 64;
    private static final int BLOCK_BUMP_YVEL = -48;
    private static final int SPRITE_SIZE_PIXELS = TILE_SPRITE_SIZE;

    private final LevelObjectType type = SUPER_MUSHROOM;
    private final Score rewardScore = SCORE_1000;

    @Value("classpath:/sprites/reward/mashroom/mushroom_normal.png")
    private ImageResource imageResource;

    private final GameEngine gameEngine;
    private final Offset offset;

    private Dimensions spriteDimensions;
    private Geometry spriteGeometry;

    private boolean expired;
    private boolean collected;
    private boolean rising = true;
    private boolean grounded;
    private boolean fallDirectionRight;
    private double pixelX;
    private double pixelY;
    private int riseTimer;
    private int riseSubCounter;
    private int collectProtectionTimer;
    private int xVelocityFixedPoint;
    private int yVelocityFixedPoint;

    @PostConstruct
    void init() {
        // Emerge from the top of the block cell (top-left aligned); the creep moves it up from here.
        pixelX = (double) offset.x() * TILE_SPRITE_SIZE;
        pixelY = (double) offset.y() * TILE_SPRITE_SIZE;
        riseTimer = RISE_TIMER_INITIAL;
        collectProtectionTimer = COLLECT_PROTECTION_FRAMES;
        // dasm Mushroom_SetFall: the mushroom rolls AWAY from the player once it lands.
        fallDirectionRight = pixelX >= closestPlayerCenterX() - SPRITE_SIZE_PIXELS / 2.0;

        spriteDimensions = new Dimensions(
            "SuperMushroom",
            imageResource.getDimensions().width() * PIXELS_TO_GAME_UNITS,
            imageResource.getDimensions().height() * PIXELS_TO_GAME_UNITS
        );
        spriteGeometry = fromTexture(gameEngine.getAssetManager(), imageResource.asTexture(), spriteDimensions);
        spriteGeometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        // Start the emerge with the sprite fully hidden; tickRise reveals it a pixel at a time.
        clipToEmergedPortion();
        positionSprite();
    }

    public void tick() {
        if (expired) {
            return;
        }

        if (collectProtectionTimer > 0) {
            collectProtectionTimer--;
        }

        if (rising) {
            tickRise();
            return;
        }

        tickMoving();
    }

    /**
     * Slow emerge out of the box (dasm {@code PowerUp_DoRaise}): after the initial hold, creep up
     * one pixel every {@link #RISE_PIXEL_INTERVAL_FRAMES} frames until the timer expires.
     */
    private void tickRise() {
        if (riseTimer < RISE_MOVEMENT_THRESHOLD) {
            if (riseSubCounter == 0) {
                pixelY -= 1;
                riseSubCounter = RISE_PIXEL_INTERVAL_FRAMES - 1;
            } else {
                riseSubCounter--;
            }
        }

        riseTimer--;
        if (riseTimer <= 0) {
            // Rise complete: hand over to the walking phase. Gravity settles it onto the block on
            // the next frame, then it starts rolling (dasm returns from PowerUp_DoRaise into
            // ObjNorm_PUpMush's ground/wall handling). Reveal the whole sprite again.
            rising = false;
            restoreFullSprite();
        } else {
            // Show only the part that has emerged above the block's top edge, so the block appears
            // to contain the rest — the NES uses a masking sprite for the same effect
            // (PowerUp_DoRaise draws with the lower half masked). We clip the quad instead, which is
            // independent of render-bucket/Z ordering.
            clipToEmergedPortion();
        }

        positionSprite();
    }

    /**
     * Walking phase (dasm {@code ObjNorm_PUpMush} + {@code Object_InteractWithWorld}): apply the
     * constant ground speed once resting, then gravity, then move and collide against the static
     * tile grid — resting on floors, reversing at walls.
     */
    private void tickMoving() {
        // dasm: DetStat & $04 (on ground) && XVel == 0 -> PowerUp_BounceXVel kicks it rolling.
        if (grounded && xVelocityFixedPoint == 0) {
            xVelocityFixedPoint = fallDirectionRight ? X_MOVE_SPEED_FIXED_POINT : -X_MOVE_SPEED_FIXED_POINT;
        }

        // Gravity (dasm Object_ApplyGravity): YVel += fall rate, capped at terminal velocity.
        yVelocityFixedPoint = min(yVelocityFixedPoint + GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);

        moveHorizontally();
        moveVertically();

        // dasm Object_InteractWithWorld @ PRG001_A97C: after moving/landing, if the block at our
        // feet is mid-bump, launch upward and about-face to stay over it.
        reactToBlockBumpBelow();

        positionSprite();

        if (hasFallenOffLevel()) {
            expired = true;
        }
    }

    /**
     * Advances the mushroom horizontally, reversing its velocity if the leading edge would enter a
     * solid tile (dasm {@code Object_AboutFace}).
     */
    private void moveHorizontally() {
        final double proposedX = pixelX + xVelocityFixedPoint * PIXELS_TO_GAME_UNITS;
        if (xVelocityFixedPoint != 0 && isWallAhead(proposedX)) {
            xVelocityFixedPoint = -xVelocityFixedPoint;
        } else {
            pixelX = proposedX;
        }
    }

    /**
     * Advances the mushroom vertically; if it would land on a solid tile, aligns its feet to that
     * tile's top edge and zeroes the fall (dasm {@code Object_HitGround}).
     */
    private void moveVertically() {
        final double proposedY = pixelY + yVelocityFixedPoint * PIXELS_TO_GAME_UNITS;
        if (yVelocityFixedPoint >= 0 && isGroundBelow(proposedY)) {
            final int feetRow = (int) floor((proposedY + SPRITE_SIZE_PIXELS) / TILE_SPRITE_SIZE);
            pixelY = (double) feetRow * TILE_SPRITE_SIZE - SPRITE_SIZE_PIXELS;
            yVelocityFixedPoint = 0;
            grounded = true;
        } else {
            pixelY = proposedY;
            grounded = false;
        }
    }

    /**
     * Reacts to the block the mushroom is resting on being bumped from below this tick (dasm
     * {@code Object_InteractWithWorld} @ PRG001_A97C, the {@code Object_TileFeet2 ==
     * TILEA_BLOCKBUMP_CLEAR} branch). Only fires while grounded — a launched mushroom is airborne
     * and off the tile, so it will not re-trigger until it lands again, by which point the 10-frame
     * bounce is long over. The launch is a fixed upward velocity; the horizontal direction is
     * <em>not</em> player-dependent (that is the enemy path, {@code Object_HandleBumpUnderneath}).
     */
    private void reactToBlockBumpBelow() {
        if (!grounded) {
            return;
        }
        final int centerColumn = (int) floor((pixelX + SPRITE_SIZE_PIXELS / 2.0) / TILE_SPRITE_SIZE);
        final int feetRow = (int) floor((pixelY + SPRITE_SIZE_PIXELS) / TILE_SPRITE_SIZE);
        if (!gameEngine.isBlockBumpActiveAt(Offset.of(centerColumn, feetRow))) {
            return;
        }
        yVelocityFixedPoint = BLOCK_BUMP_YVEL;
        grounded = false;
        applyBumpAboutFace();
    }

    /**
     * The sub-tile about-face from dasm {@code Object_InteractWithWorld} @ PRG001_A97C:
     * {@code (Objects_X << 4) EOR Objects_XVel}, testing bit 7. Reversing exactly when bit 3 of the
     * X position (the right half of the 16 px tile) differs from the X-velocity sign means the
     * mushroom turns around only when it is heading toward the near tile edge — nudging it back
     * over the block it was bumped off, rather than walking straight off.
     */
    private void applyBumpAboutFace() {
        final int subTileX = ((int) floor(pixelX)) & 0x0f;
        final boolean inRightHalf = subTileX >= 0x08;
        final boolean movingLeft = xVelocityFixedPoint < 0;
        if (inRightHalf ^ movingLeft) {
            xVelocityFixedPoint = -xVelocityFixedPoint;
        }
    }

    /**
     * Collects the mushroom: awards {@link #rewardScore} once and flags it collected. Like the
     * leaf, it lingers one more rendered frame (its manager removes it next tick) so it shares a
     * single frame with the freshly spawned "1000" caption. ({@code ObjHit_PUpMush} also grows the
     * player; that suit change is still deferred.)
     *
     * @param levelScenePlayer the player that collected the mushroom
     */
    @Override
    public void onCollisionWith(final LevelScenePlayer levelScenePlayer) {
        if (collected) {
            return;
        }
        levelScenePlayer
            .getPlayerData()
            .addToScore(rewardScore.getData().getValue());
        collected = true;
    }

    /**
     * @return {@code true} once the collect-protection window has elapsed. While raising freshly out
     *         of the box the mushroom cannot be picked up (dasm {@code Objects_Timer2}); its manager
     *         keeps it out of the collision broadphase until then.
     */
    @Override
    public boolean isCollectable() {
        return collectProtectionTimer == 0;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    private void positionSprite() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final float worldX = (float) (pixelX / TILE_SPRITE_SIZE);
        float worldY = (rows - 1) - (float) (pixelY / TILE_SPRITE_SIZE);
        if (!rising) {
            // Drop the sprite by one logical pixel (1/TILE_SPRITE_SIZE world units): its resting
            // feet are mathematically flush with the tile top, but it rasterises a pixel high,
            // leaving a visible seam above the colliding surface. This purely visual nudge closes
            // that seam and does not touch the collision/grounding math. It is skipped while rising
            // so the clipped emerging slice stays aligned with the dispensing block's top edge
            // rather than covering its top pixel line.
            worldY -= 1f / TILE_SPRITE_SIZE;
        }
        // The mushroom sprite is left/right symmetric, so — unlike the leaf — it needs no mirroring.
        spriteGeometry.setLocalTranslation(worldX, worldY, Z_DEPTH_ITEM_REWARD);
    }

    /**
     * Clips the sprite quad to just the slice that has emerged above the dispensing block's top
     * edge, hiding the rest so the block appears to contain it. The emerged height is the distance
     * the sprite's top has risen past the block cell's top edge, in game-units:
     * {@code offset.y() - pixelY / TILE_SPRITE_SIZE}. Both the quad geometry and its texture
     * coordinates are trimmed to the top {@code emerged} portion, so the visible slice always lines
     * up exactly with the block's top edge (see {@link #positionSprite()} for the world placement).
     */
    private void clipToEmergedPortion() {
        final float spriteWidth = spriteDimensions.width();
        final float spriteHeight = spriteDimensions.height();
        final float emerged = (float) clamp(offset.y() - pixelY / TILE_SPRITE_SIZE, 0.0, spriteHeight);
        final float localBottom = spriteHeight - emerged;
        final float texCoordBottom = localBottom / spriteHeight;

        final Mesh mesh = spriteGeometry.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
            0f, localBottom, 0f,
            spriteWidth, localBottom, 0f,
            spriteWidth, spriteHeight, 0f,
            0f, spriteHeight, 0f
        });
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {
            0f, texCoordBottom,
            1f, texCoordBottom,
            1f, 1f,
            0f, 1f
        });
        mesh.updateBound();
    }

    /** Restores the full, un-clipped sprite quad once the mushroom has finished emerging. */
    private void restoreFullSprite() {
        final float spriteWidth = spriteDimensions.width();
        final float spriteHeight = spriteDimensions.height();

        final Mesh mesh = spriteGeometry.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
            0f, 0f, 0f,
            spriteWidth, 0f, 0f,
            spriteWidth, spriteHeight, 0f,
            0f, spriteHeight, 0f
        });
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        });
        mesh.updateBound();
    }

    /**
     * @param proposedX the horizontal position the mushroom would occupy this frame
     * @return {@code true} if the leading edge (right when moving right, left when moving left) at
     *         the mushroom's vertical midpoint would enter a solid tile
     */
    private boolean isWallAhead(final double proposedX) {
        final int midRow = (int) floor((pixelY + SPRITE_SIZE_PIXELS / 2.0) / TILE_SPRITE_SIZE);
        final int leadingColumn = xVelocityFixedPoint > 0
            ? (int) floor((proposedX + SPRITE_SIZE_PIXELS - 1) / TILE_SPRITE_SIZE)
            : (int) floor(proposedX / TILE_SPRITE_SIZE);
        // The level's left boundary is not a wall for the mushroom: rather than about-facing back
        // to the right, it keeps rolling left off the edge and expires once fully out of bounds
        // (see hasFallenOffLevel). Real solid tiles inside the level (column >= 0) still turn it.
        if (leadingColumn < 0) {
            return false;
        }
        return gameEngine.getCollisionGrid().isSolidTile(leadingColumn, midRow);
    }

    /**
     * @param proposedY the vertical position the mushroom would occupy this frame
     * @return {@code true} if a solid tile sits under the mushroom's feet at that position
     */
    private boolean isGroundBelow(final double proposedY) {
        final int centerColumn = (int) floor((pixelX + SPRITE_SIZE_PIXELS / 2.0) / TILE_SPRITE_SIZE);
        final int feetRow = (int) floor((proposedY + SPRITE_SIZE_PIXELS) / TILE_SPRITE_SIZE);
        return gameEngine.getCollisionGrid().isSolidTile(centerColumn, feetRow);
    }

    /**
     * @return the horizontal center (sprite-pixel space) of the player nearest this mushroom — the
     *         one it rolls away from — resolved via
     *         {@link StaticEnvironmentCollisionGrid#findClosestPlayerTo(double, double)}. Falls back
     *         to this mushroom's own centre when no player exists, so the fall direction stays
     *         defined.
     */
    private double closestPlayerCenterX() {
        return gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(pixelX, pixelY)
            .map(closestPlayer -> closestPlayer.getPosition().getX() + SPRITE_SIZE_PIXELS / 2.0)
            .orElse(pixelX + SPRITE_SIZE_PIXELS / 2.0);
    }

    /**
     * @return {@code true} once the mushroom is gone for good — either it has fallen below the
     *         level floor, or (having rolled off the left boundary without about-facing) its
     *         sprite is now completely past the left edge of the level scene.
     */
    private boolean hasFallenOffLevel() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final boolean belowFloor = pixelY / TILE_SPRITE_SIZE > rows + 1;
        final boolean pastLeftEdge = pixelX + SPRITE_SIZE_PIXELS <= 0;
        return belowFloor || pastLeftEdge;
    }
}
