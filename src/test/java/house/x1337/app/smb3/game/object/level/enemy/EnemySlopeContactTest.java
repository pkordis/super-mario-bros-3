package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.SlopeTerrain;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelFrameCounter;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A real player and real Goombas over real ramp tiles, resolved through the object grid as a level tick does: what
 * a contact on a slope does to each of them.
 */
@TestInstance(PER_CLASS)
class EnemySlopeContactTest {
    private static final int BODY_SIZE = 16;
    private static final int FEET_Y = 32;
    private static final int MAX_FRAMES = 400;

    private final SlopeTerrain steepHill = new SlopeTerrain(
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "............/##\\........................",
        ".........../####\\.......................",
        "........../######\\......................",
        "########################################"
    );

    private final SlopeTerrain gentleHill = new SlopeTerrain(
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "........................................",
        "............ab##########################",
        "..........ab############################",
        "########################################"
    );

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private ActiveObjectGrid<ActiveLevelObject> objectGrid;
    private SlopeTerrain terrain;

    @BeforeEach
    void prepare() {
        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(2);
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == PlayerRuntimeState.class) {
                return new PlayerRuntimeState();
            }
            return type == GoombaAnimator.class ? goombaAnimator : mock(type);
        });
        objectGrid = new ActiveObjectGrid<>(mock(EnemySpawner.class));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Walking down a steep slope into a Goomba walking up it, the player is hurt and the Goomba lives")
    void walkingDownhillIntoAGoombaHurts() {
        // Prepare - the player on the plateau, the Goomba at the foot of the way down
        useTerrain(steepHill);
        final LevelScenePlayer player = playerAt(14 * TILE_SPRITE_SIZE, HANDLER_RIGHT);
        final Goomba goomba = goombaAt(18, false);

        // Execute
        final int frame = tickUntilTheyMeet(player, goomba);

        // Verify
        assertThat(frame).as("They met").isLessThan(MAX_FRAMES);
        assertThat(player.getRuntimeState().isChangingSize()).as("The player is hurt").isTrue();
        assertThat(goomba.isDefeated()).as("The Goomba lives").isFalse();
    }

    @Test
    @DisplayName("Walking up a steep slope into a Goomba walking down it, the player is hurt and the Goomba lives")
    void walkingUphillIntoAGoombaHurts() {
        // Prepare
        useTerrain(steepHill);
        final LevelScenePlayer player = playerAt(18 * TILE_SPRITE_SIZE, HANDLER_LEFT);
        final Goomba goomba = goombaAt(14, true);

        // Execute
        final int frame = tickUntilTheyMeet(player, goomba);

        // Verify
        assertThat(frame).as("They met").isLessThan(MAX_FRAMES);
        assertThat(player.getRuntimeState().isChangingSize()).as("The player is hurt").isTrue();
        assertThat(goomba.isDefeated()).as("The Goomba lives").isFalse();
    }

    @Test
    @DisplayName("Falling onto a Goomba on a slope still stomps it")
    void fallingOntoAGoombaOnASlopeStomps() {
        // Prepare - a Goomba standing on the way down, the player falling onto it from above
        useTerrain(steepHill);
        final Goomba goomba = goombaAt(16, false);
        goomba.setFacingRight(false);
        final LevelScenePlayer player = playerAt(16 * TILE_SPRITE_SIZE);
        player.getPosition().setY(goomba.getPosition().y() - 48);
        player.getRuntimeState().setTo(FALLING);

        // Execute
        final int frame = tickUntilTheyMeet(player, goomba);

        // Verify
        assertThat(frame).as("They met").isLessThan(MAX_FRAMES);
        assertThat(goomba.isDefeated()).as("Stomped").isTrue();
        assertThat(player.getRuntimeState().isChangingSize()).as("The player is not hurt").isFalse();
    }

    @Test
    @DisplayName("Walking into a Goomba down a steep slope with the star's invincibility kills it, unhurt")
    void anInvinciblePlayerKillsAGoombaItWalksInto() {
        // Prepare
        useTerrain(steepHill);
        final LevelScenePlayer player = playerAt(14 * TILE_SPRITE_SIZE, HANDLER_RIGHT);
        player.getRuntimeState().startStarInvincibility();
        final Goomba goomba = goombaAt(18, false);

        // Execute
        final int frame = tickUntilTheyMeet(player, goomba);

        // Verify
        assertThat(frame).as("They met").isLessThan(MAX_FRAMES);
        assertThat(goomba.isDefeated()).as("The Goomba is killed").isTrue();
        assertThat(player.getRuntimeState().isChangingSize()).as("The player is not hurt").isFalse();
    }

    @Test
    @DisplayName("Walking up a steep slope into a Goomba with the star's invincibility, the player is not hurt")
    void anInvinciblePlayerIsNeverHurt() {
        // Prepare
        useTerrain(steepHill);
        final LevelScenePlayer player = playerAt(18 * TILE_SPRITE_SIZE, HANDLER_LEFT);
        player.getRuntimeState().startStarInvincibility();
        final Goomba goomba = goombaAt(14, true);

        // Execute
        final int frame = tickUntilTheyMeet(player, goomba);

        // Verify
        assertThat(frame).as("They met").isLessThan(MAX_FRAMES);
        assertThat(player.getRuntimeState().isChangingSize()).as("The player is not hurt").isFalse();
    }

    @Test
    @DisplayName("Sliding down a steep slope into a Goomba still kills it")
    void slidingIntoAGoombaKillsIt() {
        // Prepare
        useTerrain(steepHill);
        final LevelScenePlayer player = playerAt(15 * TILE_SPRITE_SIZE - 6);
        player.updateFrame();
        holding(player, HANDLER_DOWN);
        final Goomba goomba = goombaAt(18, false);

        // Execute
        final int frame = tickUntilTheyMeet(player, goomba);

        // Verify
        assertThat(frame).as("They met").isLessThan(MAX_FRAMES);
        assertThat(goomba.isDefeated()).as("Killed").isTrue();
        assertThat(player.getRuntimeState().isChangingSize()).as("The player is not hurt").isFalse();
    }

    Stream<Arguments> slopesToMeetOn() {
        return Stream.of(
            // On the steep hill's way down (columns 15 to 17) and on the gentle hill's way up (columns 10 to 13)
            arguments("steep", steepHill, 14, 19),
            arguments("gentle", gentleHill, 9, 14)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("slopesToMeetOn")
    @DisplayName("Two Goombas walking into each other on a slope turn away from each other")
    void goombasMeetingOnASlopeTurnAway(
        final String name,
        final SlopeTerrain terrain,
        final int leftColumn,
        final int rightColumn
    ) {
        // Prepare
        useTerrain(terrain);
        final Goomba left = goombaAt(leftColumn, true);
        final Goomba right = goombaAt(rightColumn, false);

        // Execute
        int frame = 0;
        while (frame < MAX_FRAMES && left.isFacingRight()) {
            left.motionUpdate();
            right.motionUpdate();
            objectGrid.clear();
            objectGrid.insert(left);
            objectGrid.insert(right);
            objectGrid.resolveObjectToObjectBumps();
            frame++;
        }

        // Verify
        assertThat(frame).as("They met").isLessThan(MAX_FRAMES);
        assertThat(left.isFacingRight()).as("The left one turns left").isFalse();
        assertThat(right.isFacingRight()).as("The right one turns right").isTrue();
        assertThat(left.getPosition().x()).as("They met on the slope")
            .isGreaterThan((leftColumn + 1.0) * TILE_SPRITE_SIZE);
        assertThat(right.getPosition().x() - left.getPosition().x())
            .as("They did not walk through each other")
            .isPositive();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private int tickUntilTheyMeet(
        final LevelScenePlayer player,
        final Goomba goomba
    ) {
        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            player.updateFrame();
            goomba.motionUpdate();
            objectGrid.clear();
            objectGrid.insert(goomba);
            objectGrid.resolveActiveObjectCollisions(List.of(player));
            if (goomba.isDefeated() || player.getRuntimeState().isChangingSize()) {
                return frame;
            }
        }
        return MAX_FRAMES;
    }

    private void useTerrain(final SlopeTerrain terrain) {
        this.terrain = terrain;
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(terrain.columns(), terrain.rowCount()));

        final double levelBottom = terrain.rowCount() * (double) TILE_SPRITE_SIZE;
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getPendingRows()).thenReturn(terrain.rowCount());
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(0, levelBottom - 192, 640, levelBottom));
        doCallRealMethod().when(cameraState).isBelowVisibleBottom(any());

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelFrameCounter()).thenReturn(new LevelFrameCounter());
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getCollisionGrid()).thenReturn(terrain.toGrid(gameEngine));
    }

    private LevelScenePlayer playerAt(
        final double x,
        final String... handlers
    ) {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(NORMAL);
        final PlayerPosition position = player.getPosition();
        position.setX(x);
        position.setY(terrain.surfaceYAt((int) x + 8) - FEET_Y);
        position.snapshotPrevious();
        holding(player, handlers);
        return player;
    }

    private Goomba goombaAt(
        final int column,
        final boolean facingRight
    ) {
        final Goomba goomba = new Goomba(gameEngine, of(column, 0));
        goomba.init();
        goomba.setPosition(goomba.getPosition().withY(terrain.surfaceYAt(column * TILE_SPRITE_SIZE + 8) - BODY_SIZE));
        goomba.setFacingRight(facingRight);
        return goomba;
    }


    private void holding(
        final LevelScenePlayer player,
        final String... handlers
    ) {
        final PlayerInputHandler inputHandler = player.getInputHandler();
        for (final String handler : new String[] {HANDLER_LEFT, HANDLER_RIGHT, HANDLER_DOWN, HANDLER_RUN}) {
            when(inputHandler.isActive(handler)).thenReturn(false);
        }
        for (final String handler : handlers) {
            when(inputHandler.isActive(handler)).thenReturn(true);
        }
    }
}
