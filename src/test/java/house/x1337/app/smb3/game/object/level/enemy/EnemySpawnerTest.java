package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.object.level.enemy.animator.EnemyAnimator;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
 */
class EnemySpawnerTest {
    private static final int ROWS = 3;
    private static final int COLUMNS = 4;
    private static final int ANCHOR_TILE_ID = 42;
    private static final int PLACED_ROW = 1;
    private static final int PLACED_COLUMN = 2;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private Animator.Registry animatorRegistry;
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
        goombaMotionManager = new StubGoombaMotionManager();

        final LevelScene levelScene = mock(LevelScene.class);
        final LevelScene.LevelSceneLayer layer = mock(LevelScene.LevelSceneLayer.class);
        tiles = emptyLayer(ROWS, COLUMNS);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
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
    @DisplayName("A Goomba's rendering-starter tile spawns a Goomba into the manager that drives it")
    void spawnsAGoombaFromItsRenderingStarterTile() {
        // Prepare - a Goomba anchor tile painted on the NPC layer
        tiles[PLACED_ROW][PLACED_COLUMN] = enemyPartTile(ANCHOR_TILE_ID);
        when(enemyStampService.findByRenderingStarterTileId(ANCHOR_TILE_ID))
            .thenReturn(Optional.of(EnemyStamp.builder().id("goomba-1").enemyType(GOOMBA).build()));
        final Goomba goomba = mock(Goomba.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Goomba.class, gameEngine, Offset.of(PLACED_COLUMN, PLACED_ROW)))
            .thenReturn(goomba);

        // Execute
        enemySpawner.spawn();

        // Verify - the entity, anchored at its placement cell, is handed to its manager
        assertThat(goombaMotionManager.getActiveInstances()).containsExactly(goomba);
    }

    @Test
    @DisplayName("An enemy tile that anchors no enemy spawns nothing")
    void anEnemyTileThatAnchorsNoEnemySpawnsNothing() {
        // Prepare - an enemy-category tile the editor left unclassified: no record anchors it
        tiles[0][0] = enemyPartTile(99);
        when(enemyStampService.findByRenderingStarterTileId(99)).thenReturn(Optional.empty());
        when(enemyStampService.isEnemyPartTile(99)).thenReturn(false);

        // Execute
        enemySpawner.spawn();

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

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

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
