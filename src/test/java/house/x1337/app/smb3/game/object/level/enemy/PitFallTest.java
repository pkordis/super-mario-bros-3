package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.SuperMushroomType;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import house.x1337.app.smb3.game.object.level.variant.SuperMushroomVariantData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Map;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KICKED;
import static house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy.STOMP_HIT_COOLDOWN_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Walkers fall into pits now that the level's bottom edge is open (SMB3-14): the ROM's object tile probe
 * finds no tile at or below the level's bottom (dasm prg000 @ PRG000_C7F1), so nothing but a real tile
 * holds an object up. Everything here runs over a real {@link StaticEnvironmentCollisionGrid}.
 *
 * <p>The floor is the level's bottom row, with two pits in it. Before the fix every one of these walked
 * across a pit on the invisible floor one row below the level.
 */
class PitFallTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 32;
    private static final int ROWS = 8;
    private static final int FLOOR_ROW = ROWS - 1;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;
    private static final double LEVEL_BOTTOM_PIXELS = ROWS * (double) TILE_SPRITE_SIZE;
    private static final int WALK_FRAME_COUNT = 2;

    /** Pit A spans columns 10 to 15 and pit B columns 24 to 29; the floor is everywhere else. */
    private static final int PIT_A_FIRST_COLUMN = 10;
    private static final int PIT_A_LAST_COLUMN = 15;
    private static final int PIT_B_FIRST_COLUMN = 24;
    private static final int PIT_B_LAST_COLUMN = 29;

    /** The walkers start on the floor between the pits and head left, towards pit A. */
    private static final int START_COLUMN = 17;

    /** A mushroom block in the floor between the pits, so whichever way the mushroom goes it meets one. */
    private static final Offset MUSHROOM_BLOCK = Offset.of(20, FLOOR_ROW);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));

        final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
            gameEngine,
            new PowerSwitchTimeWindow()
        );
        grid.setObjects(floorWithTwoPits());
        grid.setUnderlayObjects(emptyCells());
        grid.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        when(gameEngine.getCollisionGrid()).thenReturn(grid);

        // Only the walk cycle length matters to motion; everything else the objects ask for is inert.
        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        final ParaGoombaAnimator paraGoombaAnimator = mock(ParaGoombaAnimator.class);
        when(paraGoombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        final KoopaTroopaAnimator troopaAnimator = mock(KoopaTroopaAnimator.class);
        when(troopaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(
            StaticBeanFactory.class,
            invocation -> mock((Class<?>) invocation.getArgument(0))
        );
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GoombaAnimator.class))
            .thenReturn(goombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(paraGoombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaTroopaAnimator.class))
            .thenReturn(troopaAnimator);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A walking Goomba falls into the pit instead of walking across it")
    void aGoombaFallsIntoThePit() {
        // Prepare
        final Goomba goomba = new Goomba(gameEngine, Offset.of(START_COLUMN, WALK_ROW));
        goomba.init();
        goomba.faceClosestPlayer();

        // Execute & Verify
        assertFallsIntoAPit(goomba, goomba::motionUpdate, 300);
    }

    @Test
    @DisplayName("A Para-Goomba hops into the pit and falls out of the level")
    void aParaGoombaFallsIntoThePit() {
        // Prepare
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, Offset.of(START_COLUMN, WALK_ROW));
        paraGoomba.init();
        paraGoomba.faceClosestPlayer();

        // Execute & Verify
        assertFallsIntoAPit(paraGoomba, paraGoomba::motionUpdate, 600);
    }

    @Test
    @DisplayName("A green Koopa Troopa walks off into the pit")
    void aGreenTroopaFallsIntoThePit() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(START_COLUMN, GREEN);

        // Execute & Verify
        assertFallsIntoAPit(troopa, troopa::motionUpdate, 300);
    }

    @Test
    @DisplayName("A red Koopa Troopa still turns back at the pit's edge (ObjNorm_RedTroopa)")
    void aRedTroopaTurnsBackAtThePit() {
        // Prepare
        final KoopaTroopa troopa = troopaAt(START_COLUMN, RED);

        // Execute
        for (int tick = 0; tick < 400; tick++) {
            troopa.motionUpdate();
        }

        // Verify
        assertThat(troopa.getPosition().y()).as("Still on the floor").isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(troopa.hasFallenBelowTheLevel()).isFalse();
    }

    @Test
    @DisplayName("A kicked shell rolls into the pit and falls out of the level")
    void aKickedShellFallsIntoThePit() {
        // Prepare - stomped into its shell, then kicked left by a player standing to its right
        final KoopaTroopa troopa = troopaAt(START_COLUMN, GREEN);
        final LevelScenePlayer stomper = player(troopa.getPosition().x(), 2.0);
        doCallRealMethod().when(stomper).nextKillChainReward();
        troopa.onCollisionFromAbove(stomper);
        for (int tick = 0; tick < STOMP_HIT_COOLDOWN_TICKS; tick++) {
            troopa.motionUpdate();
        }
        troopa.onKicked(player(troopa.getPosition().x() + 40, 0), false);

        // Execute & Verify
        assertThat(troopa.getState()).isEqualTo(KICKED);
        assertFallsIntoAPit(troopa, troopa::motionUpdate, 200);
    }

    @ParameterizedTest
    @EnumSource(SuperMushroomType.class)
    @DisplayName("A Super Mushroom or a 1-Up rolls off into a pit once it has risen from its block")
    void aMushroomFallsIntoAPit(final SuperMushroomType variant) {
        // Prepare
        final SuperMushroom mushroom = dispenseMushroom(variant);

        // Execute & Verify
        assertFallsIntoAPit(mushroom, mushroom::motionUpdate, 600);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * Ticks the object until its sprite's top has left the level through the bottom, and checks that it
     * did so over a pit: had it walked across one, it would have reached the floor beyond and stayed on
     * the level's rows.
     */
    private static void assertFallsIntoAPit(
        final ActiveLevelObject object,
        final Runnable tick,
        final int maxTicks
    ) {
        int ticks = 0;
        while (object.getPosition().y() < LEVEL_BOTTOM_PIXELS && ticks < maxTicks) {
            tick.run();
            ticks++;
        }
        final double x = object.getPosition().x();
        assertThat(object.getPosition().y()).as("Out through the level's bottom within %d ticks", maxTicks)
            .isGreaterThanOrEqualTo(LEVEL_BOTTOM_PIXELS);
        assertThat(isOverAPit(x)).as("Dropped through a pit, at X %s", x).isTrue();
    }

    /** Over a pit's span, allowing one tile either side for a sprite whose centre has just cleared the edge. */
    private static boolean isOverAPit(final double x) {
        return isOver(x, PIT_A_FIRST_COLUMN, PIT_A_LAST_COLUMN) || isOver(x, PIT_B_FIRST_COLUMN, PIT_B_LAST_COLUMN);
    }

    private static boolean isOver(final double x, final int firstColumn, final int lastColumn) {
        return x >= (firstColumn - 1) * (double) TILE_SPRITE_SIZE && x <= (lastColumn + 1) * (double) TILE_SPRITE_SIZE;
    }

    private KoopaTroopa troopaAt(final int column, final KoopaTroopaMode mode) {
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, Offset.of(column, WALK_ROW));
        troopa.init();
        troopa.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, mode.name())));
        troopa.faceClosestPlayer();
        return troopa;
    }

    /** A player at the given X, facing left, falling at the given speed; what a stomp and a kick read. */
    private static LevelScenePlayer player(final double x, final double dy) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(WALK_ROW_PIXEL_Y - TILE_SPRITE_SIZE);
        position.setDY(dy);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        when(player.getOrientation()).thenReturn(new PlayerOrientation(LEFT, SUSTAINED));
        when(player.getVisibility()).thenReturn(FOREGROUND);
        when(player.asLevelScenePlayer()).thenReturn(player);
        doCallRealMethod().when(player).currentKillChain();
        doCallRealMethod().when(player).kickOnContact(any());
        doCallRealMethod().when(player).kickWithoutPose(any());
        return player;
    }

    private SuperMushroom dispenseMushroom(final SuperMushroomType variant) {
        final ImageResource image = blankSprite();
        final SuperMushroom mushroom = new SuperMushroom(gameEngine, MUSHROOM_BLOCK);
        mushroom.setRewardImages(new EnumeratedImageResource<>(Map.of(
            SuperMushroomImageResource.GROWING_CAPABLE, image,
            SuperMushroomImageResource.LIFE_AWARDING_GREEN, image
        )));
        // As RewardDispensingLevelObject.onSuperMushroomDispensed does it
        mushroom.configure((SuperMushroomVariantData) variant.asLevelObjectType().getVariantData());
        return mushroom;
    }

    private static ImageResource blankSprite() {
        final ImageResource image = mock(ImageResource.class);
        when(image.getDimensions()).thenReturn(new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE));
        when(image.asTexture()).thenReturn(new Texture2D(new Image(
            RGBA8,
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE,
            createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4),
            sRGB
        )));
        return image;
    }

    private LevelObject[][] floorWithTwoPits() {
        final LevelObject solid = mock(LevelObject.class);
        when(solid.isCollidable()).thenReturn(true);

        final LevelObject[][] objects = emptyCells();
        Arrays.fill(objects[FLOOR_ROW], solid);
        Arrays.fill(objects[FLOOR_ROW], PIT_A_FIRST_COLUMN, PIT_A_LAST_COLUMN + 1, EMPTY_LEVEL_OBJECT);
        Arrays.fill(objects[FLOOR_ROW], PIT_B_FIRST_COLUMN, PIT_B_LAST_COLUMN + 1, EMPTY_LEVEL_OBJECT);
        return objects;
    }

    private LevelObject[][] emptyCells() {
        final LevelObject[][] objects = new LevelObject[ROWS][COLUMNS];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }
}
