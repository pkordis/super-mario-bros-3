package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class KoopaTroopaWalkTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;

    /** The walk cycle length, as the mode's {@code assets.json} describes it. */
    private static final int WALK_FRAME_COUNT = 2;

    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private GameEngine gameEngine;
    private boolean[][] solids;

    @BeforeEach
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }

        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        // The entity resolves its animator through the bean factory; only the cycle length matters to
        // motion, and repainting a sprite that was never built is a no-op on the stub.
        final KoopaTroopaAnimator animator = mock(KoopaTroopaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaTroopaAnimator.class))
            .thenReturn(animator);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Walks left at half a pixel per frame with no player around (GroundTroop_XVel -$08)")
    void walksLeftAtHalfAPixelPerFrame() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.faceClosestPlayer();
        final double startX = troopa.getPosition().x();

        // Execute
        tick(troopa, 16);

        // Verify
        assertThat(troopa.isFacingRight()).as("An unflipped ground troop walks left").isFalse();
        assertThat(troopa.getPosition().x())
            .as("16 frames x $08/16 px = 8 px to the left")
            .isCloseTo(startX - 8.0, within(TOLERANCE));
        assertThat(troopa.getPosition().y())
            .as("Resting on the floor: Object_HitGround re-aligns the feet every frame")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.isGrounded()).isTrue();
    }

    @Test
    @DisplayName("Appears facing the player and walks towards them (ObjInit_GroundTroop)")
    void facesThePlayerOnSpawn() {
        // Prepare - player five tiles to the right of a troopa at column 5
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        givenPlayerAtPixelX(10 * TILE_SPRITE_SIZE);
        final double startX = troopa.getPosition().x();

        // Execute
        troopa.faceClosestPlayer();
        tick(troopa, 16);

        // Verify
        assertThat(troopa.isFacingRight()).as("GroundTroop_FlipTowardsPlayer sets SPR_HFLIP").isTrue();
        assertThat(troopa.getPosition().x())
            .as("Facing is direction: the flipped troop walks right")
            .isCloseTo(startX + 8.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("About-faces at a wall without ever entering it (Object_FlipFace)")
    void turnsAroundAtAWall() {
        // Prepare - a wall at column 3, troopa starting at column 5 and walking left into it
        solidColumn(3);
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.faceClosestPlayer();

        // Execute - 40 frames is 20 px, more than the 16 px of clearance to the wall's face
        tick(troopa, 40);

        // Verify
        assertThat(troopa.isFacingRight()).as("The troopa has turned around").isTrue();
        assertThat(troopa.getPosition().x())
            .as("Never overlaps the wall: its left edge stays at or past the wall column's right edge")
            .isGreaterThanOrEqualTo(4 * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Falling accelerates by $03 a frame and caps at $40 (OBJECT_FALLRATE / OBJECT_MAXFALL)")
    void fallSpeedAcceleratesAndCaps() {
        // Prepare - dropped from the top row, so there is room to reach terminal velocity. The ledge
        // guard fires once on the way down and zeroes the fall, which is why this is measured from the
        // frame after that rather than from the drop.
        final KoopaTroopa troopa = troopaAt(5, 0);
        troopa.faceClosestPlayer();

        // Execute & Verify - Object_Move adds gravity at the end of the frame, after the move
        tick(troopa, 1);
        assertThat(troopa.getVelocity().y()).isEqualTo(3);

        tick(troopa, 20);
        assertThat(troopa.getVelocity().y())
            .as("21 frames x $03 = $3f, still one step under the cap")
            .isEqualTo(63);

        tick(troopa, 1);
        assertThat(troopa.getVelocity().y())
            .as("Terminal velocity is OBJECT_MAXFALL = $40, i.e. 4 px/frame")
            .isEqualTo(64);
    }

    @Test
    @DisplayName("A fall ends aligned on top of the floor tile it lands on (Object_HitGround)")
    void landsAlignedOnTheFloor() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, 0);
        troopa.faceClosestPlayer();

        // Execute - long enough to cross the five rows down to the floor
        tick(troopa, 60);

        // Verify
        assertThat(troopa.isGrounded()).isTrue();
        assertThat(troopa.getPosition().y())
            .as("Feet flush with the floor tile's top edge")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.isExpired()).as("Landing is not an expiry").isFalse();
    }

    @Test
    @DisplayName("The two walking frames alternate every 8 frames (Var5 accumulator cadence)")
    void walkFrameFlipsEveryEightFrames() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.faceClosestPlayer();

        // Execute & Verify
        tick(troopa, 7);
        assertThat(troopa.getWalkFrameIndex()).as("A frame holds for 8 frames").isZero();

        tick(troopa, 1);
        assertThat(troopa.getWalkFrameIndex()).as("The 8th frame flips it").isEqualTo(1);

        tick(troopa, 8);
        assertThat(troopa.getWalkFrameIndex()).as("And back again, for a 16-frame cycle").isZero();
    }

    @Test
    @DisplayName("It is a live collision participant: in the broadphase, and it bumps off other objects")
    void isACollisionParticipant() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);

        // Execute & Verify
        assertThat(troopa.isHittable())
            .as("Its manager inserts a hittable instance into the broadphase")
            .isTrue();
        assertThat(troopa.isCollidable())
            .as("An enemy is never terrain, so it is not collidable in the tile sense")
            .isFalse();
        assertThat(troopa.bouncesOffOtherObjects())
            .as("GroundTroop_BumpOffOthers runs in the walking state and Object_BumpOffOthers in the shell")
            .isTrue();
    }

    @Test
    @DisplayName("The collision box is Object_BoundBox row 01, sitting on the shell rather than the sprite")
    void collisionBoxIsTheShellOnly() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        final double originX = troopa.getPosition().x();

        // Execute & Verify - left +1, width 13, top +2, height 8
        assertThat(troopa.getBounds().left()).isCloseTo(originX + 1, within(TOLERANCE));
        assertThat(troopa.getBounds().right()).isCloseTo(originX + 14, within(TOLERANCE));
        assertThat(troopa.getBounds().top()).isCloseTo(WALK_ROW_PIXEL_Y + 2, within(TOLERANCE));
        assertThat(troopa.getBounds().bottom())
            .as("Eight pixels tall, nowhere near the 27-px sprite")
            .isCloseTo(WALK_ROW_PIXEL_Y + 10, within(TOLERANCE));
    }

    @Test
    @DisplayName("The sprite is a tile wide and two tall, reaching above the shell the position tracks")
    void spriteIsOneTileWideAndTwoTall() {
        // Prepare & Execute
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);

        // Verify
        assertThat(troopa.getSpriteDimensions().width()).isEqualTo(1.0f);
        assertThat(troopa.getSpriteDimensions().height())
            .as("Objects_Y is the top of the shell; the head is drawn a tile above it")
            .isEqualTo(2.0f);
    }

    @Test
    @DisplayName("Facing right mirrors the quad and shifts it a full width back over the same column")
    void facingRightMirrorsTheQuadInPlace() {
        // A negative X scale mirrors about the quad's bottom-left origin, so without the compensation
        // the sprite would be drawn a whole 16 px to the left of where the troopa actually is.
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        givenPlayerAtPixelX(10 * TILE_SPRITE_SIZE);
        troopa.faceClosestPlayer();
        final WorldOffset feetAnchor = WorldOffset.of(5f, 2f, Z_DEPTH_ENEMY);

        // Execute
        final WorldOffset drawOffset = troopa.spriteDrawOffset(feetAnchor);

        // Verify
        assertThat(troopa.isFacingRight()).isTrue();
        assertThat(drawOffset.x())
            .as("One quad width right, cancelling the mirror")
            .isEqualTo(feetAnchor.x() + 1.0f);
        assertThat(drawOffset.y()).isEqualTo(feetAnchor.y());
        assertThat(drawOffset.z()).isEqualTo(feetAnchor.z());
    }

    @Test
    @DisplayName("Facing left draws the art as shipped, with no offset at all")
    void facingLeftNeedsNoCompensation() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.faceClosestPlayer();
        final WorldOffset feetAnchor = WorldOffset.of(5f, 2f, Z_DEPTH_ENEMY);

        // Execute
        final WorldOffset drawOffset = troopa.spriteDrawOffset(feetAnchor);

        // Verify
        assertThat(troopa.isFacingRight()).as("The frames are drawn facing left").isFalse();
        assertThat(drawOffset.x()).isEqualTo(feetAnchor.x());
        assertThat(drawOffset.y()).isEqualTo(feetAnchor.y());
    }

    @Test
    @DisplayName("The odd walk frame is drawn one pixel lower (Troopa_YOffByFrame $06 then $05)")
    void theOddWalkFrameBobsDownOnePixel() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(5, WALK_ROW);
        troopa.faceClosestPlayer();
        final WorldOffset feetAnchor = WorldOffset.of(5f, 2f, Z_DEPTH_ENEMY);
        final float evenFrameY = troopa.spriteDrawOffset(feetAnchor).y();

        // Execute - eight frames turns the walk cycle over
        tick(troopa, 8);

        // Verify
        assertThat(troopa.getWalkFrameIndex()).isEqualTo(1);
        assertThat(troopa.spriteDrawOffset(feetAnchor).y())
            .as("World Y grows upward, so a pixel lower subtracts 1/16 of a tile")
            .isEqualTo(evenFrameY - 1f / TILE_SPRITE_SIZE);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private KoopaTroopa troopaAt(final int column, final int row) {
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, Offset.of(column, row));
        troopa.init();
        return troopa;
    }

    private void tick(final KoopaTroopa troopa, final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            troopa.motionUpdate();
        }
    }

    private void givenPlayerAtPixelX(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(WALK_ROW_PIXEL_Y);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
    }

    private void solidColumn(final int column) {
        for (int row = 0; row < ROWS; row++) {
            solids[row][column] = true;
        }
    }

    /** Mirrors {@code StaticEnvironmentCollisionGrid#isSolidTile}: outside the level is solid, sky is not. */
    private boolean isSolid(final int column, final int row) {
        if (row < 0) {
            return false;
        }
        if (row >= ROWS || column < 0 || column >= COLUMNS) {
            return true;
        }
        return solids[row][column];
    }
}
