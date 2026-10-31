package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemyDepthAllocator;
import house.x1337.app.smb3.jme3.core.DepthOrderedGeometryComparator;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_CARRIED_BEHIND_PLAYER;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_CARRIED_DETAIL_BEHIND_ITS_OBJECT;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_ENEMY_DETAIL;
import static house.x1337.app.smb3.GameConstants.Z_STEP_BETWEEN_ENEMIES;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.enemy.placement.EnemyDepthAllocator.SLOT_COUNT;
import static house.x1337.app.smb3.game.object.level.enemy.placement.EnemyDepthAllocator.baseDepthOfSlot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * SMB3-19: an enemy's body and its extra quads (a Para-Goomba's wings, a waking shell's feet) are drawn
 * as one unit against every other enemy, because each enemy owns its own base depth.
 *
 * <p>The ROM's version of the guarantee is the per-object {@code Sprite_RAM} block ({@code SprRamOffsets},
 * dasm prg000.asm:590): one object's sprites never interleave with another's. Inside the block, the wings
 * ({@code ParaGoomba_Draw}, prg004.asm:3130) and the feet (prg000.asm:2398) take the slots after the body,
 * so they pass behind it.
 *
 * <p>Before the fix every wing and foot shared one global depth below every body, so overlapping enemies
 * interleaved: one Para-Goomba's body in front of another enemy while its wings stayed behind it.
 */
class EnemyDepthOrderTest {
    private static final float TOLERANCE = 1e-7f;
    private static final int COLUMNS = 24;
    private static final int ROWS = 10;
    private static final int WALK_FRAME_COUNT = 2;
    private static final int ENEMY_COLUMN = 5;
    private static final int ENEMY_ROW = 5;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private Node rootNode;

    @BeforeEach
    void prepare() {
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        // An NPC layer with no cells: the enemies' placements resolve to nothing, so spawning erases
        // nothing from a baked texture this test does not have.
        final LevelScene.LevelSceneLayer npcLayer = mock(LevelScene.LevelSceneLayer.class);
        when(npcLayer.getTiles()).thenReturn(new Tile[0][0]);
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(levelScene.getNonPlayableCharactersLayer()).thenReturn(npcLayer);

        rootNode = new Node("root");
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getRootNode()).thenReturn(rootNode);

        final ParaGoombaAnimator paraGoombaAnimator = mock(ParaGoombaAnimator.class);
        when(paraGoombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        when(paraGoombaAnimator.buildSprite(any())).thenAnswer(invocation -> quad("ParaGoomba"));
        when(paraGoombaAnimator.buildWingSprite(any(), anyBoolean())).thenAnswer(invocation -> quad("Wing"));

        final KoopaTroopaAnimator koopaTroopaAnimator = mock(KoopaTroopaAnimator.class);
        when(koopaTroopaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        when(koopaTroopaAnimator.buildSprite(any())).thenAnswer(invocation -> quad("KoopaTroopa"));
        when(koopaTroopaAnimator.buildShellSprite(any())).thenAnswer(invocation -> quad("Shell"));
        when(koopaTroopaAnimator.buildShellFootSprite(any(), anyBoolean())).thenAnswer(invocation -> quad("Foot"));

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(paraGoombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaTroopaAnimator.class))
            .thenReturn(koopaTroopaAnimator);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    // -------------------------------------------------------------------------
    // Two overlapping enemies sort as units
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Two overlapping Para-Goombas each draw their body and wings as one unit")
    void twoParaGoombasNeverInterleave() {
        // Prepare - the older one first, as the spawner would allocate them
        final EnemyDepthAllocator allocator = new EnemyDepthAllocator();
        final ParaGoomba older = paraGoombaWithDepth(allocator.next());
        final ParaGoomba newer = paraGoombaWithDepth(allocator.next());

        // Execute
        final List<Object> drawOrder = drawOrderOf(Map.of(
            older, paraGoombaQuads(older),
            newer, paraGoombaQuads(newer)
        ));

        // Verify - case 3 of the ticket: the newer body in front, its wings still behind the older body
        assertThat(drawOrder)
            .as("Back to front: all of the older Para-Goomba, then all of the newer one")
            .containsExactly(older, older, older, newer, newer, newer);
    }

    @Test
    @DisplayName("A Para-Goomba and a waking Koopa shell never interleave, whichever spawned first")
    void aParaGoombaAndAWakingShellNeverInterleave() {
        // Prepare - wings and feet used to tie at one global depth (case 2 of the ticket)
        final EnemyDepthAllocator allocator = new EnemyDepthAllocator();
        final KoopaTroopa shell = wakingShellWithDepth(allocator.next(), false);
        final ParaGoomba paraGoomba = paraGoombaWithDepth(allocator.next());

        // Execute
        final List<Object> drawOrder = drawOrderOf(Map.of(
            shell, shellQuads(shell),
            paraGoomba, paraGoombaQuads(paraGoomba)
        ));

        // Verify
        assertThat(drawOrder).containsExactly(shell, shell, shell, paraGoomba, paraGoomba, paraGoomba);
    }

    @Test
    @DisplayName("Each enemy's depth range is contiguous and clear of the next enemy's")
    void depthRangesDoNotOverlap() {
        // Prepare
        final EnemyDepthAllocator allocator = new EnemyDepthAllocator();
        final ParaGoomba paraGoomba = paraGoombaWithDepth(allocator.next());
        final KoopaTroopa shell = wakingShellWithDepth(allocator.next(), true);

        // Execute
        final float[] paraGoombaRange = depthRange(paraGoombaQuads(paraGoomba));
        final float[] shellRange = depthRange(shellQuads(shell));

        // Verify
        assertThat(paraGoombaRange[1])
            .as("The front of the first enemy is behind the back of the second")
            .isLessThan(shellRange[0]);
        assertThat(shellRange[0] - paraGoombaRange[1])
            .as("With room left over, so float rounding cannot close the gap")
            .isGreaterThan(Z_STEP_BETWEEN_ENEMIES - 2 * Z_OFFSET_ENEMY_DETAIL - TOLERANCE);
    }

    // -------------------------------------------------------------------------
    // Part order within one enemy
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A Para-Goomba's wings stay just behind its own body")
    void wingsStayBehindTheirBody() {
        // Prepare
        final float baseDepth = baseDepthOfSlot(7);

        // Execute
        final ParaGoomba paraGoomba = paraGoombaWithDepth(baseDepth);

        // Verify - OAM slots +$08/+$0C after the body's +$00/+$04
        assertThat(depthOf(paraGoomba.getSpriteGeometry())).isCloseTo(baseDepth, within(TOLERANCE));
        assertThat(depthOf(paraGoomba.getLeftWingGeometry()))
            .isCloseTo(baseDepth - Z_OFFSET_ENEMY_DETAIL, within(TOLERANCE));
        assertThat(depthOf(paraGoomba.getRightWingGeometry()))
            .isCloseTo(baseDepth - Z_OFFSET_ENEMY_DETAIL, within(TOLERANCE));
    }

    @Test
    @DisplayName("A waking shell's feet stay just behind its own shell, upright and upside down")
    void feetStayBehindTheirShell() {
        // Prepare
        final float baseDepth = baseDepthOfSlot(3);

        // Execute
        final KoopaTroopa upright = wakingShellWithDepth(baseDepth, false);
        final KoopaTroopa upsideDown = wakingShellWithDepth(baseDepth, true);

        // Verify - Sprite_RAM+$08/+$0C against the shell's +$00/+$04 (prg000.asm:2398)
        for (final KoopaTroopa shell : List.of(upright, upsideDown)) {
            assertThat(depthOf(shell.getShellGeometry()))
                .as("Upside down: %s", shell.isUpsideDown())
                .isCloseTo(baseDepth, within(TOLERANCE));
            assertThat(depthOf(shell.getLeftShellFootGeometry()))
                .as("Upside down: %s", shell.isUpsideDown())
                .isCloseTo(baseDepth - Z_OFFSET_ENEMY_DETAIL, within(TOLERANCE));
            assertThat(depthOf(shell.getRightShellFootGeometry()))
                .as("Upside down: %s", shell.isUpsideDown())
                .isCloseTo(baseDepth - Z_OFFSET_ENEMY_DETAIL, within(TOLERANCE));
        }
    }

    // -------------------------------------------------------------------------
    // The allocator's band
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Each spawn takes the next base depth, one step in front of the last")
    void allocationStepsForward() {
        // Prepare
        final EnemyDepthAllocator allocator = new EnemyDepthAllocator();

        // Execute
        final float first = allocator.next();
        final float second = allocator.next();

        // Verify
        assertThat(second - first).isCloseTo(Z_STEP_BETWEEN_ENEMIES, within(TOLERANCE));
        assertThat(Z_OFFSET_ENEMY_DETAIL)
            .as("Parts stay well inside half a step, so no other enemy fits between them")
            .isLessThan(Z_STEP_BETWEEN_ENEMIES / 2);
    }

    @Test
    @DisplayName("The sequence wraps to the bottom of the band, and a reset restarts it")
    void allocationWrapsAndResets() {
        // Prepare
        final EnemyDepthAllocator allocator = new EnemyDepthAllocator();
        final float bottom = allocator.next();
        for (int slot = 1; slot < SLOT_COUNT; slot++) {
            allocator.next();
        }

        // Execute & Verify
        assertThat(allocator.next()).as("Slot %d wraps", SLOT_COUNT).isCloseTo(bottom, within(TOLERANCE));
        allocator.next();
        allocator.reset();
        assertThat(allocator.next()).as("A new level starts from the bottom").isCloseTo(bottom, within(TOLERANCE));
    }

    @Test
    @DisplayName("Every enemy part stays in front of reward items and behind a carried shell and the player")
    void theBandStaysBetweenItemsAndTheCarriedShell() {
        // Prepare
        final float lowestPart = baseDepthOfSlot(0) - Z_OFFSET_ENEMY_DETAIL;
        final float highestPart = baseDepthOfSlot(SLOT_COUNT - 1) + Z_OFFSET_ENEMY_DETAIL;

        // Execute
        final float carriedShellFeet = FOREGROUND.getPlayerZ()
            - Z_OFFSET_CARRIED_BEHIND_PLAYER
            - Z_OFFSET_CARRIED_DETAIL_BEHIND_ITS_OBJECT;

        // Verify
        assertThat(lowestPart).as("In front of the reward items").isGreaterThan(Z_DEPTH_ITEM_REWARD);
        assertThat(highestPart)
            .as("Behind every part of a shell carried by the foreground player")
            .isLessThan(carriedShellFeet);
        assertThat(highestPart).as("And behind the player").isLessThan(FOREGROUND.getPlayerZ());
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private ParaGoomba paraGoombaWithDepth(final float baseDepth) {
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, Offset.of(ENEMY_COLUMN, ENEMY_ROW));
        paraGoomba.init();
        paraGoomba.setBaseDepth(baseDepth);
        paraGoomba.spawnIntoScene();
        return paraGoomba;
    }

    /** A Koopa Troopa in its shell at the same spot, with the feet placed whether or not they flicker on. */
    private KoopaTroopa wakingShellWithDepth(final float baseDepth, final boolean upsideDown) {
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, Offset.of(ENEMY_COLUMN, ENEMY_ROW));
        troopa.init();
        troopa.setBaseDepth(baseDepth);
        troopa.spawnIntoScene();
        troopa.setState(SHELLED);
        troopa.setUpsideDown(upsideDown);
        troopa.positionSprite();
        return troopa;
    }

    private static List<Geometry> paraGoombaQuads(final ParaGoomba paraGoomba) {
        return List.of(
            paraGoomba.getSpriteGeometry(),
            paraGoomba.getLeftWingGeometry(),
            paraGoomba.getRightWingGeometry()
        );
    }

    private static List<Geometry> shellQuads(final KoopaTroopa troopa) {
        return List.of(
            troopa.getShellGeometry(),
            troopa.getLeftShellFootGeometry(),
            troopa.getRightShellFootGeometry()
        );
    }

    /**
     * Sorts every quad with the renderer's own comparator and reports, back to front, which enemy each
     * one belongs to.
     */
    private List<Object> drawOrderOf(final Map<Object, List<Geometry>> quadsByEnemy) {
        final Map<Geometry, Object> owners = new IdentityHashMap<>();
        quadsByEnemy.forEach((enemy, quads) -> quads.forEach(quad -> owners.put(quad, enemy)));
        rootNode.updateGeometricState();
        final List<Geometry> sorted = new ArrayList<>(owners.keySet());
        sorted.sort(new DepthOrderedGeometryComparator());
        return sorted.stream().map(owners::get).toList();
    }

    /** @return the {@code [back, front]} depth of the given quads */
    private static float[] depthRange(final List<Geometry> quads) {
        float back = Float.MAX_VALUE;
        float front = -Float.MAX_VALUE;
        for (final Geometry quad : quads) {
            back = Math.min(back, depthOf(quad));
            front = Math.max(front, depthOf(quad));
        }
        return new float[] {back, front};
    }

    private static float depthOf(final Geometry geometry) {
        return geometry.getLocalTranslation().z;
    }

    private static Geometry quad(final String name) {
        return new Geometry(name, new Quad(1f, 1f));
    }
}
