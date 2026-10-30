package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.enumeration.SuperMushroomType;
import house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperLeafMotionManager;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperMushroomMotionManager;
import house.x1337.app.smb3.game.object.level.variant.SuperMushroomVariantData;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Optional;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.SuperMushroomType.GROWING_CAPABLE;
import static house.x1337.app.smb3.game.object.level.ActiveLevelObject.FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Power-ups the player leaves behind are deleted off screen and never come back, as dasm prg000
 * {@code Object_DeleteOffScreen} does for them: every power-up's normal routine calls it each frame
 * (prg001 {@code ObjNorm_PUpMush}, {@code ObjNorm_PUp1UpMush}, {@code ObjNorm_SuperLeaf}), except a
 * mushroom still held in its block ({@code PowerUp_DoRaise} jumps past the call while the timer is
 * {@code >= $2d}). Between the delete edges nothing freezes a power-up.
 */
class PowerUpOffScreenDeletionTest {
    private static final double TOLERANCE = 1e-6;
    private static final int COLUMNS = 40;
    private static final int ROWS = 8;
    private static final Offset BLOCK = Offset.of(3, 4);
    private static final double BLOCK_X = BLOCK.x() * (double) TILE_SPRITE_SIZE;
    private static final int HOLD_FRAMES = 16;
    private static final double SCREEN_WIDTH_PIXELS = 256;
    private static final double SCREEN_HEIGHT_PIXELS = 240;

    private SuperMushroomMotionManager mushrooms;
    private SuperLeafMotionManager leaves;
    private GameEngine gameEngine;
    private LevelScene levelScene;
    private AxisAlignedBoundingBox screen;

    @BeforeEach
    void prepare() {
        mushrooms = new SuperMushroomMotionManager();
        leaves = new SuperLeafMotionManager();

        levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        // The dispensing block is the only terrain, and it is not mid-bump.
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isBlock(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isBlock(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        // The screen the managers see; each test moves it with givenTheScreenAt.
        givenTheScreenAt(0, 0);
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt())).thenAnswer(invocation -> screen);

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getActiveObjectGrid()).thenReturn(new ActiveObjectGrid<ActiveLevelObject>(
            mock(EnemySpawner.class)
        ));
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));
    }

    // -------------------------------------------------------------------------
    // The delete edges
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "Screen from {0} to {1}: deleted = {2}")
    @CsvSource({
        // X = 48: kept up to exactly 32 px right of the screen, deleted a pixel further
        "-240, 16, false",
        "-241, 15, true",
        // and kept until it is exactly 48 px left of the screen
        "95, 351, false",
        "96, 352, true"
    })
    @DisplayName("A rising mushroom is deleted at the ROM's delete edges, 48 px left and 32 px right of the screen")
    void aMushroomIsDeletedAtTheDeleteEdges(final double left, final double right, final boolean deleted) {
        // Prepare - past the hold, so the mushroom creeps up in place at X = 48
        final SuperMushroom mushroom = dispenseMushroom(GROWING_CAPABLE);
        tickMushrooms(HOLD_FRAMES);
        screen = new AxisAlignedBoundingBox(left, 0, right, SCREEN_HEIGHT_PIXELS);

        // Execute
        tickMushrooms(1);

        // Verify
        assertThat(mushroom.getPosition().x()).as("Still at the block's X").isCloseTo(BLOCK_X, within(TOLERANCE));
        assertThat(mushrooms.getActiveInstances().contains(mushroom)).as("In play").isEqualTo(!deleted);
        assertThat(mushroom.getSpriteGeometry().getParent() == null).as("Detached").isEqualTo(deleted);
    }

    @Test
    @DisplayName("A deleted mushroom does not come back when the player returns, and earns nothing")
    void aDeletedMushroomIsGoneForGood() {
        // Prepare - deleted past the screen's left delete edge
        final SuperMushroom mushroom = dispenseMushroom(GROWING_CAPABLE);
        tickMushrooms(HOLD_FRAMES);
        givenTheScreenAt(BLOCK_X + 48, 0);
        tickMushrooms(1);

        // Execute - the player scrolls back over the block
        givenTheScreenAt(0, 0);
        tickMushrooms(60);

        // Verify
        assertThat(mushrooms.getActiveInstances()).as("Nothing respawns it").isEmpty();
        assertThat(mushrooms.getActiveScorePopups()).as("No caption").isEmpty();
        assertThat(mushroom.isCollected()).as("Not collected").isFalse();
        assertThat(mushroom.isExpired()).as("Expired").isTrue();
    }

    @Test
    @DisplayName("A mushroom held in its block is kept for the 16-frame hold however far the screen is")
    void aMushroomHeldInItsBlockIsNotDeleted() {
        // Prepare - the screen far to the right of the block from the start
        final SuperMushroom mushroom = dispenseMushroom(GROWING_CAPABLE);
        givenTheScreenAt(1000, 0);

        // Execute & Verify - PowerUp_DoRaise skips Object_DeleteOffScreen while the timer is >= $2d
        for (int frame = 1; frame <= HOLD_FRAMES; frame++) {
            tickMushrooms(1);
            assertThat(mushrooms.getActiveInstances()).as("Frame %d: held", frame).containsExactly(mushroom);
        }

        // Execute - the first rise frame reaches the call at PRG001_A937
        tickMushrooms(1);

        // Verify
        assertThat(mushrooms.getActiveInstances()).as("Deleted on the first rise frame").isEmpty();
    }

    // -------------------------------------------------------------------------
    // No freeze between the edges
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A leaf out of sight but inside the delete edges keeps moving")
    void aLeafOutOfSightKeepsMoving() {
        // Prepare - the screen well below the leaf, and 40 px to its right: out of sight on both axes,
        // but short of the left delete edge, which a non-vertical level's test is limited to
        final SuperLeaf leaf = dispenseLeaf(BLOCK);
        givenTheScreenAt(BLOCK_X + 40, 300);
        final double startX = leaf.getPosition().x();
        final double startY = leaf.getPosition().y();

        // Execute - the whole rise, then into the flutter
        tickLeaves(40);

        // Verify
        assertThat(leaves.getActiveInstances()).as("Still in play").containsExactly(leaf);
        assertThat(leaf.getPosition().y()).as("It rose").isNotEqualTo(startY);
        assertThat(leaf.getPosition().x()).as("And started to flutter").isNotEqualTo(startX);
    }

    // -------------------------------------------------------------------------
    // Below the level
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A leaf at Y 512 is deleted and one above it is not, as Objects_YHi >= 2 decides")
    void aLeafAtTheTwoPageLineIsDeleted() {
        // Prepare - spawned 14 px above their cells; the first rise frame lifts them 2 px, to 512 and 496
        final SuperLeaf fallen = dispenseLeaf(Offset.of(BLOCK.x(), 33));
        final SuperLeaf notYet = dispenseLeaf(Offset.of(BLOCK.x(), 32));
        givenTheScreenAt(0, 300);

        // Execute
        tickLeaves(1);

        // Verify
        assertThat(fallen.getPosition().y()).isCloseTo(FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS, within(TOLERANCE));
        assertThat(leaves.getActiveInstances()).as("Only the one above the line").containsExactly(notYet);
    }

    @Test
    @DisplayName("In a level taller than 32 rows the line drops to the level's bottom edge")
    void aTallerLevelMovesTheLineDown() {
        // Prepare - 40 rows: the line is at 640 px, so 512 is still inside the level
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, 40));
        final SuperLeaf leaf = dispenseLeaf(Offset.of(BLOCK.x(), 33));
        givenTheScreenAt(0, 300);

        // Execute
        tickLeaves(1);

        // Verify
        assertThat(leaves.getActiveInstances()).as("Still inside the level").containsExactly(leaf);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void givenTheScreenAt(final double left, final double top) {
        screen = new AxisAlignedBoundingBox(left, top, left + SCREEN_WIDTH_PIXELS, top + SCREEN_HEIGHT_PIXELS);
    }

    private SuperMushroom dispenseMushroom(final SuperMushroomType variant) {
        final ImageResource image = blankSprite();
        final SuperMushroom mushroom = new SuperMushroom(gameEngine, BLOCK);
        mushroom.setRewardImages(new EnumeratedImageResource<>(Map.of(
            SuperMushroomImageResource.GROWING_CAPABLE, image,
            SuperMushroomImageResource.LIFE_AWARDING_GREEN, image
        )));
        // As RewardDispensingLevelObject.onSuperMushroomDispensed does it
        mushroom.configure((SuperMushroomVariantData) variant.asLevelObjectType().getVariantData());
        mushrooms.spawn(mushroom);
        return mushroom;
    }

    private SuperLeaf dispenseLeaf(final Offset block) {
        final SuperLeaf leaf = new SuperLeaf(gameEngine, block);
        leaf.setImageResource(blankSprite());
        leaf.init();
        leaves.spawn(leaf);
        return leaf;
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

    private void tickMushrooms(final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            gameEngine.getActiveObjectGrid().clear();
            mushrooms.update();
        }
    }

    private void tickLeaves(final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            gameEngine.getActiveObjectGrid().clear();
            leaves.update();
        }
    }

    private static boolean isBlock(final int column, final int row) {
        return column == BLOCK.x() && row == BLOCK.y();
    }
}
