package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GoombaWalkTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;

    /** The ground Goomba's walk cycle length, as its {@code assets.json} describes it. */
    private static final int WALK_FRAME_COUNT = 2;

    /** The floor the Goomba walks on, with its walking row directly above it. */
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
        when(collisionGrid.isSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        // The entity resolves its animator through the bean factory; only the cycle length matters to
        // motion, and repainting a sprite that was never built is a no-op on the stub.
        final GoombaAnimator animator = mock(GoombaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GoombaAnimator.class))
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
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        final double startX = goomba.getPixelX();

        // Execute
        tick(goomba, 16);

        // Verify
        assertThat(goomba.isFacingRight()).as("An unflipped ground troop walks left").isFalse();
        assertThat(goomba.getPixelX())
            .as("16 frames x $08/16 px = 8 px to the left")
            .isCloseTo(startX - 8.0, within(TOLERANCE));
        assertThat(goomba.getPixelY())
            .as("Resting on the floor: Object_HitGround re-aligns the feet every frame")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(goomba.isGrounded()).isTrue();
    }

    @Test
    @DisplayName("Appears facing the player and walks towards them (ObjInit_GroundTroop)")
    void facesThePlayerOnSpawn() {
        // Prepare - player five tiles to the right of a Goomba at column 5
        final Goomba goomba = goombaAt(5, WALK_ROW);
        givenPlayerAtPixelX(10 * TILE_SPRITE_SIZE);
        final double startX = goomba.getPixelX();

        // Execute
        goomba.faceClosestPlayer();
        tick(goomba, 16);

        // Verify
        assertThat(goomba.isFacingRight()).as("GroundTroop_FlipTowardsPlayer sets SPR_HFLIP").isTrue();
        assertThat(goomba.getPixelX())
            .as("Facing is direction: the flipped troop walks right")
            .isCloseTo(startX + 8.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("About-faces at a wall without ever entering it (Object_FlipFace)")
    void turnsAroundAtAWall() {
        // Prepare - a wall at column 3, Goomba starting at column 5 and walking left into it
        solidColumn(3);
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();

        // Execute - 40 frames is 20 px, more than the 16 px of clearance to the wall's face
        tick(goomba, 40);

        // Verify
        assertThat(goomba.isFacingRight()).as("The Goomba has turned around").isTrue();
        assertThat(goomba.getPixelX())
            .as("Never overlaps the wall: its left edge stays at or past the wall column's right edge")
            .isGreaterThanOrEqualTo(4 * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("Walks off a ledge and falls - the ground troop has no ledge turn")
    void walksOffALedgeAndFalls() {
        // Prepare - the floor stops after column 4; the Goomba starts at column 6 heading left into the gap
        removeFloorFrom(0, 4);
        final Goomba goomba = goombaAt(6, WALK_ROW);
        goomba.faceClosestPlayer();

        // Execute - 48 frames is 24 px, which leaves its centre still over the last floor tile
        tick(goomba, 48);

        // Verify
        assertThat(goomba.isGrounded()).as("Still supported while its centre is over the floor").isTrue();

        // Execute - one more step takes the centre past the edge, then it falls for 12 frames
        tick(goomba, 13);

        // Verify
        assertThat(goomba.isFacingRight()).as("No about-face: only the red troopa avoids ledges").isFalse();
        assertThat(goomba.isGrounded()).isFalse();
        assertThat(goomba.getPixelY())
            .as("Falling below the row it was walking on")
            .isGreaterThan(WALK_ROW_PIXEL_Y);
    }

    @Test
    @DisplayName("Falling accelerates by $03 a frame and caps at $40 (OBJECT_FALLRATE / OBJECT_MAXFALL)")
    void fallSpeedAcceleratesAndCaps() {
        // Prepare - dropped from the top row, so there is room to reach terminal velocity
        final Goomba goomba = goombaAt(5, 0);
        goomba.faceClosestPlayer();

        // Execute & Verify - Object_Move adds gravity at the end of the frame, after the move
        tick(goomba, 1);
        assertThat(goomba.getYVelocityFixedPoint()).isEqualTo(3);

        tick(goomba, 20);
        assertThat(goomba.getYVelocityFixedPoint())
            .as("21 frames x $03 = $3f, still one step under the cap")
            .isEqualTo(63);

        tick(goomba, 1);
        assertThat(goomba.getYVelocityFixedPoint())
            .as("Terminal velocity is OBJECT_MAXFALL = $40, i.e. 4 px/frame")
            .isEqualTo(64);
    }

    @Test
    @DisplayName("A fall ends aligned on top of the floor tile it lands on (Object_HitGround)")
    void landsAlignedOnTheFloor() {
        // Prepare
        final Goomba goomba = goombaAt(5, 0);
        goomba.faceClosestPlayer();

        // Execute - long enough to cross the five rows down to the floor
        tick(goomba, 60);

        // Verify
        assertThat(goomba.isGrounded()).isTrue();
        assertThat(goomba.getPixelY())
            .as("Feet flush with the floor tile's top edge")
            .isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(goomba.isExpired()).as("Landing is not an expiry").isFalse();
    }

    @Test
    @DisplayName("The two walking frames alternate every 8 frames (Var7/Var5 accumulator cadence)")
    void walkFrameFlipsEveryEightFrames() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();

        // Execute & Verify
        tick(goomba, 7);
        assertThat(goomba.getWalkFrameIndex()).as("A frame holds for 8 frames").isZero();

        tick(goomba, 1);
        assertThat(goomba.getWalkFrameIndex()).as("The 8th frame flips it").isEqualTo(1);

        tick(goomba, 8);
        assertThat(goomba.getWalkFrameIndex()).as("And back again, for a 16-frame cycle").isZero();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Goomba goombaAt(final int column, final int row) {        final Goomba goomba = new Goomba(gameEngine, Offset.of(column, row));
        goomba.init();
        return goomba;
    }

    private void tick(final Goomba goomba, final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            goomba.motionUpdate();
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

    private void removeFloorFrom(final int fromColumn, final int toColumn) {
        for (int column = fromColumn; column <= toColumn; column++) {
            solids[FLOOR_ROW][column] = false;
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
