package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.object.level.enemy.animator.EnemyAnimator;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the runtime half of enemy placement: a level places an enemy by painting its {@code ENEMY_PART}
 * tiles, anchored by the enemy's rendering-starter tile ({@code EnemyStamper}), and this spawner turns
 * that anchor back into a live {@link Goomba} handed to the manager that drives it.
 *
 * <p>Guards the fix for the disconnect that left a placed Goomba frozen: the spawner used to resolve
 * placements through {@code LevelObjectService}, which has no record for an {@code ENEMY_PART} tile, so
 * no enemy was ever created and the stamped tiles just sat there. Also pins that the per-level reset of
 * the enemy animators' scene-bound assets happens here — not in a motion manager, which owns behaviour
 * and physics rather than drawing.
 *
 * <p>And pins the placement lifecycle (SMB3-3), the ROM's {@code Level_ObjectsSpawnByScroll}: the first
 * tick spawns the opening screen, after that the one look-ahead column picked by the last scroll direction
 * spawns, a live enemy leaving the screen releases its placement to spawn again, and a dead one keeps it
 * spent.
 */
class EnemySpawnerTest {
    private static final int ROWS = 3;
    private static final int COLUMNS = 40;
    private static final int ANCHOR_TILE_ID = 42;
    private static final int PLACED_ROW = 1;
    private static final int PLACED_COLUMN = 2;
    private static final Offset PLACED_AT = Offset.of(PLACED_COLUMN, PLACED_ROW);
    private static final double SCREEN_WIDTH_PIXELS = 256;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private Animator.Registry animatorRegistry;
    private CameraState cameraState;
    private EnemyStampService enemyStampService;
    private GameEngine gameEngine;
    private StubGoombaMotionManager goombaMotionManager;
    private Tile[][] tiles;
    private EnemySpawner enemySpawner;

    @BeforeEach
    void prepare() {
        enemyStampService = mock(EnemyStampService.class);
        gameEngine = mock(GameEngine.class);
        animatorRegistry = mock(Animator.Registry.class);
        cameraState = mock(CameraState.class);
        goombaMotionManager = new StubGoombaMotionManager();

        final LevelScene levelScene = mock(LevelScene.class);
        final LevelScene.LevelSceneLayer layer = mock(LevelScene.LevelSceneLayer.class);
        tiles = emptyLayer(ROWS, COLUMNS);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(levelScene.getNonPlayableCharactersLayer()).thenReturn(layer);
        when(layer.getTiles()).thenReturn(tiles);
        // The spawner routes a placed enemy to the manager owning its type, found among the engine's
        // motion managers. doReturn bypasses the wildcard capture on List<? extends MotionManager<?>>.
        doReturn(List.of(goombaMotionManager)).when(gameEngine).getMotionManagers();

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Animator.Registry.class))
            .thenReturn(animatorRegistry);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(EnemyStampService.class))
            .thenReturn(enemyStampService);
        enemySpawner = new EnemySpawner(gameEngine);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A Goomba's rendering-starter tile spawns a Goomba into its manager on the first tick")
    void spawnsAGoombaFromItsRenderingStarterTile() {
        // Prepare - a Goomba anchor tile painted on the NPC layer, on the opening screen
        givenAPlacedGoombaAt(PLACED_COLUMN);
        enemySpawner.spawn();
        assertThat(goombaMotionManager.getActiveInstances())
            .as("Reading the level instantiates nothing")
            .isEmpty();
        givenTheScreenAt(0);

        // Execute
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify - the entity, anchored at its placement cell, is handed to its manager
        assertThat(goombaMotionManager.getActiveInstances()).hasSize(1);
        staticBeanFactory.verify(() -> StaticBeanFactory.getBean(Goomba.class, gameEngine, PLACED_AT));
    }

    @Test
    @DisplayName("The opening pass spawns every column from 32 px left of the screen to 16 px right of it")
    void theOpeningPassCoversTheOpeningScreen() {
        // Prepare - the screen spans x = 64 to 320; PRG005_BF49 walks the look-ahead from 32 to 336
        givenAPlacedGoombaAt(1);
        givenAPlacedGoombaAt(2);
        givenAPlacedGoombaAt(21);
        givenAPlacedGoombaAt(22);
        enemySpawner.spawn();
        givenTheScreenAt(64);

        // Execute
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify - column 2 starts at 32 and column 21 at 336; columns 1 and 22 are just outside
        assertThat(goombaMotionManager.getActiveInstances()).hasSize(2);
        staticBeanFactory.verify(() -> StaticBeanFactory.getBean(Goomba.class, gameEngine, at(2)));
        staticBeanFactory.verify(() -> StaticBeanFactory.getBean(Goomba.class, gameEngine, at(21)));
    }

    @Test
    @DisplayName("Scrolling right, a placement spawns when the look-ahead 16 px past the right edge reaches it")
    void scrollingRightSpawnsAtTheRightLookAhead() {
        // Prepare - column 18 starts at x = 288, just past the opening pass's reach of 272
        givenAPlacedGoombaAt(18);
        enemySpawner.spawn();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();

        // Execute - the screen scrolls right to x = 15, whose look-ahead (15 + 256 + 16) is still column 17
        for (int screenLeft = 1; screenLeft <= 15; screenLeft++) {
            givenTheScreenAt(screenLeft);
            enemySpawner.spawnPlacementsComingIntoView();
        }

        // Verify
        assertThat(goombaMotionManager.getActiveInstances()).as("Not yet").isEmpty();

        // Execute - one more pixel puts the look-ahead on 288
        givenTheScreenAt(16);
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(goombaMotionManager.getActiveInstances()).hasSize(1);
    }

    @Test
    @DisplayName("Scrolling left, a placement spawns when the look-ahead 32 px past the left edge reaches it")
    void scrollingLeftSpawnsAtTheLeftLookAhead() {
        // Prepare - the opening pass at x = 320 reaches down to column 18; column 17 spans 272 to 288
        givenAPlacedGoombaAt(17);
        enemySpawner.spawn();
        givenTheScreenAt(320);
        enemySpawner.spawnPlacementsComingIntoView();
        assertThat(goombaMotionManager.getActiveInstances()).as("Outside the opening pass").isEmpty();

        // Execute - one pixel left puts the look-ahead on 287
        givenTheScreenAt(319);
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(goombaMotionManager.getActiveInstances()).hasSize(1);
    }

    @Test
    @DisplayName("A placement spawns once, not once per tick, while the look-ahead stays on its column")
    void aPlacementSpawnsOnce() {
        // Prepare
        givenAPlacedGoombaAt(18);
        enemySpawner.spawn();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();
        givenTheScreenAt(16);

        // Execute
        enemySpawner.spawnPlacementsComingIntoView();
        enemySpawner.spawnPlacementsComingIntoView();
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify - Level_ObjectsSpawned bit 7 is set by the first spawn
        assertThat(goombaMotionManager.getActiveInstances()).hasSize(1);
    }

    @Test
    @DisplayName("A released placement in the look-ahead column respawns at once, even while the screen is still")
    void aReleasedPlacementAtTheLookAheadRespawnsAtOnce() {
        // Prepare - spawned at the right look-ahead, then deleted alive while the screen held still
        givenAPlacedGoombaAt(18);
        enemySpawner.spawn();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();
        givenTheScreenAt(16);
        enemySpawner.spawnPlacementsComingIntoView();
        final Goomba first = goombaMotionManager.getActiveInstances().removeFirst();
        enemySpawner.releasePlacementOf(first);

        // Execute - the screen does not move
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify - Level_ObjectsSpawnByScroll runs every frame on Scroll_LastDir's look-ahead, so a fresh
        // instance, built from the placement again, comes straight back
        assertThat(goombaMotionManager.getActiveInstances())
            .as("Respawned")
            .hasSize(1)
            .doesNotContain(first);
        staticBeanFactory.verify(
            () -> StaticBeanFactory.getBean(Goomba.class, gameEngine, at(18)),
            times(2)
        );
    }

    @Test
    @DisplayName("A released placement away from the look-ahead waits for the scroll to bring it back")
    void aReleasedPlacementWaitsForTheLookAhead() {
        // Prepare - spawned by the opening pass at column 2 (x 32 to 48), then deleted alive
        givenAPlacedGoombaAt(PLACED_COLUMN);
        enemySpawner.spawn();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();
        enemySpawner.releasePlacementOf(goombaMotionManager.getActiveInstances().removeFirst());

        // Execute - still, then right to x = 100: the look-ahead never sits on column 2
        enemySpawner.spawnPlacementsComingIntoView();
        for (int screenLeft = 1; screenLeft <= 100; screenLeft++) {
            givenTheScreenAt(screenLeft);
            enemySpawner.spawnPlacementsComingIntoView();
        }

        // Verify
        assertThat(goombaMotionManager.getActiveInstances()).as("Not while it is away").isEmpty();

        // Execute - back left until the left look-ahead (x - 32) reaches 47, column 2's last pixel
        for (int screenLeft = 99; screenLeft >= 79; screenLeft--) {
            givenTheScreenAt(screenLeft);
            enemySpawner.spawnPlacementsComingIntoView();
        }

        // Verify - a new instance, at its authored cell with fresh state
        assertThat(goombaMotionManager.getActiveInstances()).as("Respawned on the way back").hasSize(1);
        staticBeanFactory.verify(
            () -> StaticBeanFactory.getBean(Goomba.class, gameEngine, PLACED_AT),
            times(2)
        );
    }

    @Test
    @DisplayName("A placement whose enemy died never spawns again, however the screen moves")
    void aRetiredPlacementNeverRespawns() {
        // Prepare - spawned at the look-ahead, then killed (Object_SetDeadEmpty keeps the spawned bit set)
        givenAPlacedGoombaAt(18);
        enemySpawner.spawn();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();
        givenTheScreenAt(16);
        enemySpawner.spawnPlacementsComingIntoView();
        enemySpawner.retirePlacementOf(goombaMotionManager.getActiveInstances().removeFirst());

        // Execute - held still on its column, then away and back
        enemySpawner.spawnPlacementsComingIntoView();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();
        givenTheScreenAt(16);
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(goombaMotionManager.getActiveInstances()).isEmpty();
    }

    @Test
    @DisplayName("A placement handed to the enemy taking over the slot is released by that enemy, not the first")
    void aHandedOverPlacementFollowsTheSlot() {
        // Prepare - the Para-Goomba's Goomba takes its slot (Objects_SpawnIdx stays put)
        givenAPlacedGoombaAt(18);
        enemySpawner.spawn();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();
        givenTheScreenAt(16);
        enemySpawner.spawnPlacementsComingIntoView();
        final Goomba predecessor = goombaMotionManager.getActiveInstances().removeFirst();
        final Goomba successor = mock(Goomba.class);
        enemySpawner.handOverPlacement(predecessor, successor);

        // Execute - the predecessor is thrown away, then the successor leaves the screen alive
        enemySpawner.retirePlacementOf(predecessor);
        enemySpawner.spawnPlacementsComingIntoView();
        assertThat(goombaMotionManager.getActiveInstances())
            .as("Retiring the predecessor no longer speaks for the placement")
            .isEmpty();
        enemySpawner.releasePlacementOf(successor);
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify - the successor's release did
        assertThat(goombaMotionManager.getActiveInstances()).as("The placement came back").hasSize(1);
    }

    @Test
    @DisplayName("Reading a new level forgets the previous level's placements and runs the opening pass again")
    void aNewLevelForgetsThePreviousPlacements() {
        // Prepare - one level with a Goomba, played; then one without
        givenAPlacedGoombaAt(PLACED_COLUMN);
        enemySpawner.spawn();
        givenTheScreenAt(0);
        enemySpawner.spawnPlacementsComingIntoView();
        tiles[PLACED_ROW][PLACED_COLUMN] = NULL_TILE;
        goombaMotionManager.getActiveInstances().clear();
        enemySpawner.spawn();

        // Execute
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(goombaMotionManager.getActiveInstances()).isEmpty();
    }

    @Test
    @DisplayName("An enemy tile that anchors no enemy spawns nothing")
    void anEnemyTileThatAnchorsNoEnemySpawnsNothing() {
        // Prepare - an enemy-category tile the editor left unclassified: no record anchors it
        tiles[0][0] = enemyPartTile(99);
        when(enemyStampService.findByRenderingStarterTileId(99)).thenReturn(Optional.empty());
        when(enemyStampService.isEnemyPartTile(99)).thenReturn(false);
        givenTheScreenAt(0);

        // Execute
        enemySpawner.spawn();
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(goombaMotionManager.getActiveInstances()).isEmpty();
    }

    @Test
    @DisplayName("Setting up a level drops the enemy animators' assets and the previous population")
    void levelSetupResetsAnimatorsAndPopulation() {
        // Prepare - a manager still holding the previous level's Goomba
        goombaMotionManager.getActiveInstances().add(mock(Goomba.class));

        // Execute - no placements this level, so only the resets are observable
        enemySpawner.spawn();

        // Verify - the animators' scene-bound assets are dropped here, by the enemy world's level setup,
        // selected by family so the tile-bound animators' registration is untouched, and the stale
        // population is gone
        verify(animatorRegistry).resetAll(EnemyAnimator.class);
        assertThat(goombaMotionManager.getActiveInstances()).isEmpty();
    }

    @Test
    @DisplayName("The enemy's stored custom data reaches configure, together with its type's mode")
    void storedDataReachesConfigure() {
        // Prepare
        givenAPlacedGoombaAt(PLACED_COLUMN, Map.of("speed", "fast"));
        enemySpawner.spawn();
        givenTheScreenAt(0);

        // Execute
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(configuredData()).containsOnly(
            entry("speed", "fast"),
            entry(MODE_ATTRIBUTE, GOOMBA_NORMAL.getMode().name())
        );
    }

    @Test
    @DisplayName("The enemy type's mode overrides a mode entry in the stored custom data")
    void theTypesModeOverridesAStoredMode() {
        // Prepare - a stored mode, which the editor refuses to save, naming something else
        givenAPlacedGoombaAt(PLACED_COLUMN, Map.of(MODE_ATTRIBUTE, "SOMETHING_ELSE", "speed", "fast"));
        enemySpawner.spawn();
        givenTheScreenAt(0);

        // Execute
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(configuredData())
            .as("The EnemyType is the single source of truth for the mode")
            .containsEntry(MODE_ATTRIBUTE, GOOMBA_NORMAL.getMode().name())
            .containsEntry("speed", "fast");
    }

    @Test
    @DisplayName("An enemy with no stored custom data is configured with its type's mode alone")
    void noStoredDataConfiguresTheModeAlone() {
        // Prepare - a stamp whose data is null, as an unconverted legacy stamp could be
        givenAPlacedGoombaAt(PLACED_COLUMN, null);
        enemySpawner.spawn();
        givenTheScreenAt(0);

        // Execute
        enemySpawner.spawnPlacementsComingIntoView();

        // Verify
        assertThat(configuredData()).containsOnly(entry(MODE_ATTRIBUTE, GOOMBA_NORMAL.getMode().name()));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** Paints a Goomba's anchor in the given column of the NPC layer; each spawn from it builds a new Goomba. */
    private void givenAPlacedGoombaAt(final int column) {
        givenAPlacedGoombaAt(column, Map.of());
    }

    /** As {@link #givenAPlacedGoombaAt(int)}, for a Goomba stamp carrying the given custom data. */
    private void givenAPlacedGoombaAt(final int column, final Map<String, Object> data) {
        tiles[PLACED_ROW][column] = enemyPartTile(ANCHOR_TILE_ID);
        final EnemyStamp stamp = EnemyStamp.builder().id("goomba-1").enemyType(GOOMBA_NORMAL).data(data).build();
        when(enemyStampService.findByRenderingStarterTileId(ANCHOR_TILE_ID)).thenReturn(Optional.of(stamp));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Goomba.class, gameEngine, at(column)))
            .thenAnswer(invocation -> mock(Goomba.class));
    }

    /** The attributes the one spawned Goomba was configured with. */
    private Map<String, Object> configuredData() {
        assertThat(goombaMotionManager.getActiveInstances()).hasSize(1);
        final ArgumentCaptor<LevelObjectData> captor = ArgumentCaptor.forClass(LevelObjectData.class);
        verify(goombaMotionManager.getActiveInstances().getFirst()).configure(captor.capture());
        return captor.getValue().asMap();
    }

    /** The screen, 256 px wide like the NES's, with its left edge at the given sprite-pixel X. */
    private void givenTheScreenAt(final double left) {
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(left, -1000, left + SCREEN_WIDTH_PIXELS, 1000));
    }

    private static Offset at(final int column) {
        return Offset.of(column, PLACED_ROW);
    }

    /** A manager owning {@link Goomba}, holding whatever the spawner hands it. */
    private static final class StubGoombaMotionManager implements EnemyMotionManager<Goomba> {
        private final List<Goomba> activeInstances = new ArrayList<>();
        private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();

        @Override
        public List<Goomba> getActiveInstances() {
            return activeInstances;
        }

        @Override
        public List<ScorePopupAnimation> getActiveScorePopups() {
            return activeScorePopups;
        }

        @Override
        public Class<Goomba> getType() {
            return Goomba.class;
        }
    }

    private static Tile enemyPartTile(final int id) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, 0xFFFF0000);
        return Tile
            .builder()
            .id(id)
            .type(TileType.ENEMY_PART)
            .argbData(pixels)
            .build();
    }

    private static Tile[][] emptyLayer(final int rows, final int columns) {
        final Tile[][] tiles = new Tile[rows][columns];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
