package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.reward.core.PowerUpCollectProtection;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.object.level.variant.ConfigurableVariant;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.variant.SuperMushroomVariantData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static java.lang.Math.clamp;
import static java.lang.Math.floor;

/**
 * A Super Mushroom power-up.
 *
 * <h2>Motion — ported from dasm {@code prg001.asm}</h2>
 * <p>Unlike the {@link SuperLeaf} (which pops up ballistically then flutters and ignores the
 * world), the mushroom follows the {@code ObjInit_PUpMush} / {@code ObjNorm_PUpMush} contract and
 * is a genuine <em>ground-walking</em> item. It has two phases:
 *
 * <ol>
 *   <li><b>Rise ({@code PowerUp_DoRaise} @ PRG001_A8C6).</b> The block spawns the mushroom one pixel
 *       above its own cell (prg001.asm:2282-2289, {@code SUB #$01}). The object's timer starts at
 *       {@code $3d} (61 frames). While it is still {@code >= $2d} (the first 16 frames) the routine
 *       branches straight out: no move, no draw, no hit test. Below {@code $2d} the mushroom creeps
 *       up exactly 1 px every 3 frames (dasm decrements {@code Objects_Var1} from 2 and nudges
 *       {@code Objects_Y} by −1 on underflow): 15 px, which with the spawn pixel is exactly one tile.
 *       The collect protection ({@code Objects_Timer2 = $10}) runs out on the last hold frame, so the
 *       first rise frame is already collectable; what limits the pickup is the {@code OAT_BOUNDBOX01}
 *       box ({@link RewardLevelObject#getBounds()}), which must reach the player's.</li>
 *   <li><b>Move ({@code ObjNorm_PUpMush} @ PRG001_A735).</b> Gravity ({@code OBJECT_FALLRATE = $03}
 *       per frame, capped at {@code OBJECT_MAXFALL = $40}) pulls it onto the block it emerged from;
 *       once grounded with no horizontal velocity, {@code PowerUp_BounceXVel} kicks it rolling at a
 *       constant {@code $10} (1 px/frame) <em>away</em> from the player (fall direction chosen by
 *       {@code Mushroom_SetFall} @ PRG001_A94A). {@code Object_InteractWithWorld} then walks it
 *       across the static tile grid: it rests on solid floors and reverses direction
 *       ({@code Object_AboutFace}) when it runs into a wall.</li>
 * </ol>
 *
 * <p>All velocities are 4.4 fixed-point ({@code value/16 == px/frame}), carried in a
 * {@link house.x1337.app.smb3.model.game.motion.FixedPointVelocity}. World collision is resolved
 * directly against the scene's {@link StaticEnvironmentCollisionGrid} — the same tile-aligned solids
 * the player uses — which is why the mushroom, alone among the reward items so far, needs a
 * reference to it. ({@code ObjHit_PUpMush} also grows the player / grants the Super suit; that suit
 * change is still deferred, exactly as the leaf's Raccoon grant is.)
 */
@Data
@Prototype
@RequiredArgsConstructor
public final class SuperMushroom implements RewardLevelObject, ConfigurableVariant<SuperMushroomVariantData> {
    private static final int RISE_TIMER_INITIAL = 61;
    private static final int RISE_MOVEMENT_THRESHOLD = 45;
    private static final int RISE_PIXEL_INTERVAL_FRAMES = 3;
    private static final int SPAWN_Y_OFFSET_PIXELS = -1;
    private static final int X_MOVE_SPEED_FIXED_POINT = 16;
    private static final int GRAVITY_FIXED_POINT = 3;
    private static final int MAX_FALL_FIXED_POINT = 64;
    private static final int BLOCK_BUMP_YVEL = -48;
    private static final int SPRITE_SIZE_PIXELS = TILE_SPRITE_SIZE;

    @Value("house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource")
    private EnumeratedImageResource<SuperMushroomImageResource> rewardImages;

    private final PowerUpCollectProtection collectProtection = new PowerUpCollectProtection();
    private final GameEngine gameEngine;
    private final Offset offset;

    private LevelObjectType type;
    private SuperMushroomImageResource imageResourceType;
    private Reward rewardType;

    private Dimensions spriteDimensions;
    private Geometry spriteGeometry;

    private boolean expired;
    private boolean collected;
    private boolean rising = true;
    private boolean grounded;
    private boolean fallDirectionRight;
    private int riseTimer;
    private int riseSubCounter;
    private PixelPosition position;
    private FixedPointVelocity velocity = STILL;

    @Override
    public void configure(final SuperMushroomVariantData variantData) {
        variantData.applyTo(this);

        // One pixel above the block cell's origin (prg001.asm:2282-2289): the 15 px creep then ends
        // exactly on the block's top edge.
        position = PixelPosition.ofTile(offset).plusY(SPAWN_Y_OFFSET_PIXELS);
        riseTimer = RISE_TIMER_INITIAL;
        // dasm Mushroom_SetFall: the mushroom rolls AWAY from the player once it lands.
        fallDirectionRight = position.x() >= closestPlayerCenterX() - SPRITE_SIZE_PIXELS / 2.0;

        final ImageResource imageResource = getImageResource();
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

    public void motionUpdate() {
        if (expired) {
            return;
        }

        collectProtection.tick();

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
        riseTimer--;
        if (riseTimer <= 0) {
            // Rise complete: hand over to the walking phase. The creep has already put the feet flush
            // on the block's top edge, so the first walking frame lands it without moving it (dasm
            // returns from PowerUp_DoRaise into ObjNorm_PUpMush's ground/wall handling). Reveal the
            // whole sprite again.
            rising = false;
            restoreFullSprite();
            positionSprite();
            return;
        }

        if (!isHoldingInTheBlock()) {
            if (riseSubCounter == 0) {
                position = position.plusY(-1);
                riseSubCounter = RISE_PIXEL_INTERVAL_FRAMES - 1;
            } else {
                riseSubCounter--;
            }
        }

        // Show only the part that has emerged above the block's top edge, so the block appears to
        // contain the rest — the NES uses a masking sprite for the same effect (PowerUp_DoRaise draws
        // tile $77 behind the background over the lower part). We clip the quad instead, which is
        // independent of render-bucket/Z ordering.
        clipToEmergedPortion();
        positionSprite();
    }

    private boolean isHoldingInTheBlock() {
        return rising && riseTimer >= RISE_MOVEMENT_THRESHOLD;
    }

    @Override
    public boolean isExemptFromDeletionOffScreen() {
        return isHoldingInTheBlock();
    }

    /**
     * Walking phase (dasm {@code ObjNorm_PUpMush} + {@code Object_InteractWithWorld}): apply the
     * constant ground speed once resting, then gravity, then move and collide against the static
     * tile grid — resting on floors, reversing at walls.
     */
    private void tickMoving() {
        // dasm: DetStat & $04 (on ground) && XVel == 0 -> PowerUp_BounceXVel kicks it rolling.
        if (grounded && velocity.stationaryX()) {
            velocity = velocity.directedX(fallDirectionRight, X_MOVE_SPEED_FIXED_POINT);
        }

        // Gravity (dasm Object_ApplyGravity): YVel += fall rate, capped at terminal velocity.
        velocity = velocity.falling(GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);

        moveHorizontally();
        moveVertically();

        // dasm Object_InteractWithWorld @ PRG001_A97C: after moving/landing, if the block at our
        // feet is mid-bump, launch upward and about-face to stay over it.
        reactToBlockBumpBelow();

        positionSprite();
    }

    /**
     * Advances the mushroom horizontally, reversing its velocity if the leading edge would enter a
     * solid tile (dasm {@code Object_AboutFace}).
     */
    private void moveHorizontally() {
        final PixelPosition proposed = position.steppedX(velocity);
        if (!velocity.stationaryX() && isWallAhead(proposed)) {
            velocity = velocity.negatedX();
        } else {
            position = proposed;
        }
    }

    /**
     * Advances the mushroom vertically; if it would land on a solid tile, aligns its feet to that
     * tile's top edge and zeroes the fall (dasm {@code Object_HitGround}).
     */
    private void moveVertically() {
        final PixelPosition proposed = position.steppedY(velocity);
        if (velocity.descending() && isGroundBelow(proposed)) {
            final int feetRow = proposed.plusY(SPRITE_SIZE_PIXELS).row();
            position = position.withY((double) feetRow * TILE_SPRITE_SIZE - SPRITE_SIZE_PIXELS);
            velocity = velocity.withY(0);
            grounded = true;
        } else {
            position = proposed;
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
        final int centerColumn = position.plusX(SPRITE_SIZE_PIXELS / 2.0).column();
        final int feetRow = position.plusY(SPRITE_SIZE_PIXELS).row();
        if (!getCollisionGrid().isBlockBumpActiveAt(Offset.of(centerColumn, feetRow))) {
            return;
        }
        velocity = velocity.withY(BLOCK_BUMP_YVEL);
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
        final int subTileX = ((int) floor(position.x())) & 0x0f;
        final boolean inRightHalf = subTileX >= 0x08;
        if (inRightHalf ^ velocity.movingLeft()) {
            velocity = velocity.negatedX();
        }
    }

    @Override
    public ImageResource getImageResource() {
        return rewardImages.select(imageResourceType);
    }

    /**
     * Collects the mushroom: awards {@link #rewardType} once and flags it collected. Like the
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
            .add(rewardType.getData());
        collected = true;
        // dasm ObjHit_PUpMush: a small player grows into Super (mode SHRUNK →
        // NORMAL) via the grow transition; a player who is already big just
        // banks the points, so this no-ops for them.
        levelScenePlayer.onRewardConsumption(this);
    }

    /**
     * Whether the hit test runs this frame, which is all {@code PowerUp_DoHitTest} gates: not during the
     * hold, where {@code PowerUp_DoRaise} never reaches it, and not while {@code Objects_Timer2} runs. The
     * two end together, so in practice this opens on the first rise frame, with one pixel of the
     * mushroom out of the block. There is deliberately no "far enough out" threshold: the ROM has none,
     * and the {@link #getBounds() box} is what keeps a hidden mushroom out of reach. Its manager keeps it
     * out of the collision broadphase until then.
     *
     * @return {@code true} once the mushroom can be collected
     */
    @Override
    public boolean isCollectable() {
        return collectProtection.isElapsed() && !isHoldingInTheBlock();
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    /**
     * The mushroom disappears on contact (dasm {@code ObjHit_PUpMush} sets it
     * {@code OBJSTATE_DEADEMPTY} immediately): it is detached the same tick it is
     * collected, before the grow freeze begins, leaving only its rising "1000"
     * caption on screen during the transition.
     */
    @Override
    public boolean detachesOnCollect() {
        return true;
    }

    private void positionSprite() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final float worldX = (float) (position.x() / TILE_SPRITE_SIZE);
        float worldY = (rows - 1) - (float) (position.y() / TILE_SPRITE_SIZE);
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
     * the sprite's top lies above the block cell's top edge, in game-units:
     * {@code offset.y() - position.y() / TILE_SPRITE_SIZE}. Both the quad geometry and its texture
     * coordinates are trimmed to the top {@code emerged} portion, so the visible slice always lines
     * up exactly with the block's top edge (see {@link #positionSprite()} for the world placement).
     *
     * <p>The spawn pixel puts the sprite's top one pixel above the block before the creep begins, so
     * the hold is hidden explicitly: {@code PowerUp_DoRaise} does not draw the mushroom at all then.
     */
    private void clipToEmergedPortion() {
        final float spriteWidth = spriteDimensions.width();
        final float spriteHeight = spriteDimensions.height();
        final float emerged = isHoldingInTheBlock()
            ? 0f
            : (float) clamp(offset.y() - position.y() / TILE_SPRITE_SIZE, 0.0, spriteHeight);
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

    private boolean isWallAhead(final PixelPosition proposed) {
        final int midRow = position.plusY(SPRITE_SIZE_PIXELS / 2.0).row();
        final int leadingColumn = velocity.movingRight()
            ? proposed.plusX(SPRITE_SIZE_PIXELS - 1).column()
            : proposed.column();
        // The level's sides are not a wall for the mushroom (the grid reports them open): rather than
        // about-facing, it keeps rolling off the edge, and its manager deletes it once it is past the
        // screen's delete edge (the camera is clamped at the level's ends).
        return gameEngine.getCollisionGrid().isWallOrCeilingSolidTile(leadingColumn, midRow);
    }

    /**
     * @param proposed the position the mushroom would occupy this frame
     * @return {@code true} if a solid tile sits under the mushroom's feet there
     */
    private boolean isGroundBelow(final PixelPosition proposed) {
        final int centerColumn = position.plusX(SPRITE_SIZE_PIXELS / 2.0).column();
        final int feetRow = proposed.plusY(SPRITE_SIZE_PIXELS).row();
        return gameEngine.getCollisionGrid().isGroundSolidTile(centerColumn, feetRow);
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
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + SPRITE_SIZE_PIXELS / 2.0)
            .orElse(position.x() + SPRITE_SIZE_PIXELS / 2.0);
    }
}
