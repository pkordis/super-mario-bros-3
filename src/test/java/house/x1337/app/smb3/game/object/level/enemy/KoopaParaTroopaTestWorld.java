package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaParaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.Getter;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A level for Koopa Paratroopa tests: a floor across the whole width, nothing off the sides (the grid is
 * open there since SMB3-1), mocked animators that hand out bare quads, and a bean factory that builds a
 * real {@link KoopaTroopa} when the paratroopa hands its slot over, so the hand-over is asserted on real
 * state rather than a mock's.
 */
@Getter
final class KoopaParaTroopaTestWorld implements AutoCloseable {
    static final int COLUMNS = 40;
    static final int ROWS = 12;
    static final int FLOOR_ROW = 10;
    static final int REST_ROW = FLOOR_ROW - 1;
    static final double REST_ROW_PIXEL_Y = REST_ROW * (double) TILE_SPRITE_SIZE;
    static final int START_COLUMN = 10;
    static final int WALK_FRAME_COUNT = 2;

    private final boolean[][] solids = new boolean[ROWS][COLUMNS];
    private final Set<Offset> bumpedBlockCells = new HashSet<>();
    private final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
    private final ActiveObjectGrid<ActiveLevelObject> activeObjectGrid;
    private final GameEngine gameEngine = mock(GameEngine.class);
    private final Node rootNode = new Node("root");
    private final KoopaParaTroopaAnimator animator = mock(KoopaParaTroopaAnimator.class);
    private final KoopaTroopaAnimator troopaAnimator = mock(KoopaTroopaAnimator.class);
    private final TailAttackFlashMotionManager tailAttackFlashMotionManager = mock(TailAttackFlashMotionManager.class);
    private final MockedStatic<StaticBeanFactory> staticBeanFactory;

    @SuppressWarnings("unchecked")
    KoopaParaTroopaTestWorld() {
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(collisionGrid.isBlockBumpActiveAt(any()))
            .thenAnswer(invocation -> bumpedBlockCells.contains(invocation.<Offset>getArgument(0)));

        // An NPC layer with no cells: a placement resolves to nothing, so spawning erases nothing.
        final LevelScene.LevelSceneLayer npcLayer = mock(LevelScene.LevelSceneLayer.class);
        when(npcLayer.getTiles()).thenReturn(new Tile[0][0]);
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(levelScene.getNonPlayableCharactersLayer()).thenReturn(npcLayer);

        activeObjectGrid = mock(ActiveObjectGrid.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getActiveObjectGrid()).thenReturn(activeObjectGrid);
        when(gameEngine.getRootNode()).thenReturn(rootNode);

        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        when(animator.buildSprite(any())).thenAnswer(invocation -> quad("KoopaParaTroopa"));
        when(animator.buildWingSprite(any())).thenAnswer(invocation -> quad("Wing"));
        when(troopaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        when(troopaAnimator.buildSprite(any())).thenAnswer(invocation -> quad("KoopaTroopa"));
        when(troopaAnimator.buildShellSprite(any())).thenAnswer(invocation -> quad("Shell"));
        when(troopaAnimator.buildShellFootSprite(any(), anyBoolean())).thenAnswer(invocation -> quad("Foot"));

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaParaTroopaAnimator.class))
            .thenReturn(animator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaTroopaAnimator.class))
            .thenReturn(troopaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TailAttackFlashMotionManager.class))
            .thenReturn(tailAttackFlashMotionManager);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(KoopaTroopa.class), any(), any()))
            .thenAnswer(invocation -> {
                final KoopaTroopa troopa = new KoopaTroopa(gameEngine, invocation.getArgument(2));
                troopa.init();
                return troopa;
            });
    }

    @Override
    public void close() {
        staticBeanFactory.close();
    }

    KoopaParaTroopa paratroopaAt(final int column, final int row) {
        final KoopaParaTroopa paratroopa = new KoopaParaTroopa(gameEngine, Offset.of(column, row));
        paratroopa.init();
        return paratroopa;
    }

    /** The red, flying paratroopa: the same class, configured as the spawner configures it. */
    KoopaParaTroopa redParatroopaAt(final int column, final int row) {
        final KoopaParaTroopa paratroopa = paratroopaAt(column, row);
        paratroopa.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, RED.name())));
        return paratroopa;
    }

    /** A paratroopa resting on the floor and in the scene, so it has a body and a wing to place. */
    KoopaParaTroopa spawnedParatroopa() {
        final KoopaParaTroopa paratroopa = paratroopaAt(START_COLUMN, REST_ROW);
        paratroopa.spawnIntoScene();
        return paratroopa;
    }

    void tick(final KoopaParaTroopa paratroopa, final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            paratroopa.motionUpdate();
        }
    }

    /** @return the single troopa the paratroopa handed its slot to */
    KoopaTroopa capturedReplacement() {
        final ArgumentCaptor<EnemyLevelObject> captor = ArgumentCaptor.forClass(EnemyLevelObject.class);
        verify(activeObjectGrid).spawnEnemy(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(KoopaTroopa.class);
        return (KoopaTroopa) captor.getValue();
    }

    /** Marks the cell under the paratroopa's feet as carrying a live block bounce. */
    void bumpTheBlockUnder(final KoopaParaTroopa paratroopa) {
        final int centerColumn = paratroopa.getPosition().plusX(TILE_SPRITE_SIZE / 2.0).column();
        final int feetRow = paratroopa.getPosition().plusY(TILE_SPRITE_SIZE).row();
        bumpedBlockCells.add(Offset.of(centerColumn, feetRow));
    }

    /** A player at a sprite-pixel X on the rest row, published to the closest-player query. */
    LevelScenePlayer playerAtPixelX(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(REST_ROW_PIXEL_Y);
        position.setDY(1.0);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        doCallRealMethod().when(player).currentKillChain();
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
        return player;
    }

    /** A player falling onto the enemy; its position and its kill chain are read by the stomp. */
    static LevelScenePlayer descendingPlayer() {
        final PlayerPosition position = new PlayerPosition();
        position.setDY(2.0);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        return player;
    }

    void solidColumn(final int column) {
        for (int row = 0; row < ROWS; row++) {
            solids[row][column] = true;
        }
    }

    void solidRow(final int row) {
        for (int column = 0; column < COLUMNS; column++) {
            solids[row][column] = true;
        }
    }

    void openFloorFrom(final int column) {
        for (int open = column; open < COLUMNS; open++) {
            solids[FLOOR_ROW][open] = false;
        }
    }

    /** Mirrors the real grid: open above, below and beside the level, solid only where painted. */
    private boolean isSolid(final int column, final int row) {
        if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
            return false;
        }
        return solids[row][column];
    }

    private static Geometry quad(final String name) {
        return new Geometry(name, new Quad(1f, 1f));
    }
}
