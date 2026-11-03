package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode;
import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.effect.PoofMotionManager;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.VenusFireTrapAnimator;
import house.x1337.app.smb3.game.object.level.projectile.PiranhaFireball;
import house.x1337.app.smb3.game.object.level.projectile.PiranhaFireballAnimator;
import house.x1337.app.smb3.game.object.level.projectile.PiranhaFireballMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.Getter;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A level with one pipe for Venus Fire Trap tests: the plant's stamp sits with its rendering starter on the
 * cell above the pipe's top-left tile, a 256x240 view at the origin, World 1, no player until a test adds
 * one, animators that hand out bare quads, and a real fireball manager, so a spit is observable as a real
 * fireball in a real slot.
 */
@Getter
final class VenusFireTrapTestWorld implements AutoCloseable {
    static final int COLUMNS = 40;
    static final int ROWS = 15;
    static final int PIPE_COLUMN = 10;
    static final int PIPE_ROW = 8;
    static final int STARTER_ROW = PIPE_ROW - 1;
    static final double PLANT_X = PIPE_COLUMN * (double) TILE_SPRITE_SIZE + 8;
    static final double ORIGIN_Y = PIPE_ROW * (double) TILE_SPRITE_SIZE;
    static final double SCREEN_WIDTH = 256;
    static final double SCREEN_HEIGHT = 240;
    static final int SAFETY_TICKS = 1000;

    private final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
    private final ActiveObjectGrid<ActiveLevelObject> activeObjectGrid;
    private final GameEngine gameEngine = mock(GameEngine.class);
    private final CameraState cameraState = mock(CameraState.class);
    private final PlayerData playerData = new PlayerData();
    private final Node rootNode = new Node("root");
    private final VenusFireTrapAnimator animator = mock(VenusFireTrapAnimator.class);
    private final PiranhaFireballAnimator fireballAnimator = mock(PiranhaFireballAnimator.class);
    private final PiranhaFireballMotionManager fireballMotionManager = new PiranhaFireballMotionManager();
    private final TailAttackFlashMotionManager tailAttackFlashMotionManager = mock(TailAttackFlashMotionManager.class);
    private final PoofMotionManager poofMotionManager = mock(PoofMotionManager.class);
    private final MockedStatic<StaticBeanFactory> staticBeanFactory;

    @SuppressWarnings("unchecked")
    VenusFireTrapTestWorld() {
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

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
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getPlayerData()).thenReturn(playerData);
        viewAt(0, 0);

        when(animator.buildSprite(any())).thenAnswer(invocation -> quad("Head"));
        when(animator.buildStemSprite(any())).thenAnswer(invocation -> quad("Stem"));
        when(fireballAnimator.buildSprite(any())).thenAnswer(invocation -> quad("Fireball"));

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(VenusFireTrapAnimator.class))
            .thenReturn(animator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PiranhaFireballAnimator.class))
            .thenReturn(fireballAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PiranhaFireballMotionManager.class))
            .thenReturn(fireballMotionManager);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(PiranhaFireball.class), any()))
            .thenAnswer(invocation -> new PiranhaFireball(invocation.getArgument(1)));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TailAttackFlashMotionManager.class))
            .thenReturn(tailAttackFlashMotionManager);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PoofMotionManager.class))
            .thenReturn(poofMotionManager);
    }

    @Override
    public void close() {
        staticBeanFactory.close();
    }

    /** Moves the camera so its view's top-left is at the given sprite pixels. */
    void viewAt(final double left, final double top) {
        when(cameraState.getActiveObjectRegion(0))
            .thenReturn(new AxisAlignedBoundingBox(left, top, left + SCREEN_WIDTH, top + SCREEN_HEIGHT));
    }

    /** A plant placed on the pipe and configured as the spawner configures it, not yet in the scene. */
    VenusFireTrap plant(final PiranhaPlantMode mode) {
        final VenusFireTrap plant = new VenusFireTrap(gameEngine, Offset.of(PIPE_COLUMN, STARTER_ROW));
        plant.init();
        plant.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, mode.name())));
        return plant;
    }

    /** A plant in the scene, so it has a head and a stem to place. */
    VenusFireTrap spawnedPlant(final PiranhaPlantMode mode) {
        final VenusFireTrap plant = plant(mode);
        plant.spawnIntoScene();
        return plant;
    }

    void tick(final VenusFireTrap plant, final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            plant.motionUpdate();
        }
    }

    /**
     * Ticks until the plant changes into the given state.
     *
     * @return the number of ticks taken, the changing one included
     */
    int tickUntil(final VenusFireTrap plant, final PiranhaPlantState state) {
        for (int tick = 1; tick <= SAFETY_TICKS; tick++) {
            plant.motionUpdate();
            if (plant.getState() == state) {
                return tick;
            }
        }
        throw new IllegalStateException("The plant never reached " + state);
    }

    int fireballCount() {
        return fireballMotionManager.getActiveFireballs().size();
    }

    /** A player at sprite pixels, published to the closest-player query. */
    LevelScenePlayer playerAt(final double pixelX, final double pixelY) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(pixelY);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        doCallRealMethod().when(player).nextKillChainReward();
        doCallRealMethod().when(player).currentKillChain();
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
        return player;
    }

    private static Geometry quad(final String name) {
        return new Geometry(name, new Quad(1f, 1f));
    }
}
