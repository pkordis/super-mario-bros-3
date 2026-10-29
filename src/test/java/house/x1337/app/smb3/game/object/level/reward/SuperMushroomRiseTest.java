package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.SuperMushroomType;
import house.x1337.app.smb3.enumeration.resource.SuperMushroomImageResource;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.spawner.EnemySpawner;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperMushroomMotionManager;
import house.x1337.app.smb3.game.object.level.variant.SuperMushroomVariantData;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.SuperMushroomType.GROWING_CAPABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A block mushroom (Super or 1-Up) rising out of its block: when it can be collected, from where, and
 * where it ends up.
 *
 * <p>The ROM's rules (dasm prg001 {@code ObjInit_PUpMush} / {@code PowerUp_DoRaise} /
 * {@code PowerUp_DoHitTest}): spawned one pixel above the block cell; held, undrawn and un-hit-tested
 * for 16 frames; then creeping up a pixel every 3 frames, hit-tested on every one of them. The only
 * thing between a rising mushroom and the player is its {@code OAT_BOUNDBOX01} box, tested inclusively
 * by {@code ObjectObject_Intersect}. The frames below are this entity's own ticks, the first being the
 * tick it was dispensed on.
 */
class SuperMushroomRiseTest {
    private static final double TOLERANCE = 1e-6;
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Offset BLOCK = Offset.of(3, 4);
    private static final double BLOCK_X = BLOCK.x() * (double) TILE_SPRITE_SIZE;
    private static final double BLOCK_TOP_Y = BLOCK.y() * (double) TILE_SPRITE_SIZE;
    private static final int HOLD_FRAMES = 16;
    private static final int LAST_RISE_FRAME = 61;
    /** Rise frames 1, 4, ... 43 (dasm Objects_Var1 counting down from 2), i.e. ticks 17, 20, ... 59. */
    private static final int FIRST_CREEP_FRAME = HOLD_FRAMES + 1;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private ActiveObjectGrid<ActiveLevelObject> broadPhase;
    private SuperMushroomMotionManager manager;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        manager = new SuperMushroomMotionManager();

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        // The dispensing block is the only terrain, and it is not mid-bump.
        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isBlock(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isBlock(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(-1000, -1000, 1000, 1000));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getActiveObjectGrid()).thenReturn(broadPhase);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));

        // The reward's points travel in a prototype bean; collecting needs a real one.
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Reward.Data.class))
            .thenAnswer(invocation -> new Reward.Data());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    // -------------------------------------------------------------------------
    // The hold
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(SuperMushroomType.class)
    @DisplayName("For its first 16 frames it neither moves, shows nor can be collected, even by a player on it")
    void theHoldHidesItAndKeepsItOutOfReach(final SuperMushroomType variant) {
        // Prepare - a player whose box overlaps the mushroom's from the very first frame, which no real
        // approach could manage: only the hold keeps it out of reach
        final SuperMushroom mushroom = dispense(variant);
        final LevelScenePlayer player = largePlayerAt(BLOCK_X, BLOCK_TOP_Y - 24);

        // Execute & Verify
        for (int frame = 1; frame <= HOLD_FRAMES; frame++) {
            tick(player);
            assertThat(mushroom.getPosition().y()).as("Frame %d: not moving", frame).isEqualTo(BLOCK_TOP_Y - 1);
            assertThat(visiblePixels(mushroom)).as("Frame %d: not drawn", frame).isZero();
            assertThat(mushroom.isCollectable()).as("Frame %d: not hit-tested", frame).isFalse();
            assertThat(mushroom.isCollected()).as("Frame %d: not collected", frame).isFalse();
        }

        // Execute - the first rise frame
        tick(player);

        // Verify - Timer2 ran out with the hold, and nothing else stands in the way
        assertThat(mushroom.getPosition().y()).as("The first pixel of the creep").isEqualTo(BLOCK_TOP_Y - 2);
        assertThat(mushroom.isCollected()).as("Collectable on the very first rise frame").isTrue();
    }

    // -------------------------------------------------------------------------
    // From above, from below
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(SuperMushroomType.class)
    @DisplayName("A large player standing on the block collects it on exactly the frame 4 px of it show")
    void fromAboveItIsCollectedWithFourPixelsShowing(final SuperMushroomType variant) {
        // Prepare - feet on the block's top edge, directly over it
        final SuperMushroom mushroom = dispense(variant);
        final LevelScenePlayer player = largePlayerAt(BLOCK_X, BLOCK_TOP_Y - 32);

        // Execute & Verify - the third creep pixel lands on frame 23
        for (int frame = 1; frame < 23; frame++) {
            tick(player);
            assertThat(mushroom.isCollected())
                .as("Frame %d: %d px showing is not enough", frame, visiblePixels(mushroom))
                .isFalse();
        }
        assertThat(visiblePixels(mushroom)).as("The frame before: 3 px above the block").isEqualTo(3);

        tick(player);
        assertThat(visiblePixels(mushroom)).as("4 px above the block").isEqualTo(4);
        assertThat(mushroom.isCollected()).as("Collected at the ROM's k = 3").isTrue();
        assertThat(mushroom.isRising()).as("Well before the rise finishes").isTrue();
    }

    @Test
    @DisplayName("A small player standing on the block collects it on the same frame: both boxes end at +30")
    void fromAboveASmallPlayerMeetsItOnTheSameFrame() {
        // Prepare
        final SuperMushroom mushroom = dispense(GROWING_CAPABLE);
        final LevelScenePlayer player = smallPlayerAt(BLOCK_X, BLOCK_TOP_Y - 32);

        // Execute
        tick(player, 22);
        final boolean collectedEarly = mushroom.isCollected();
        tick(player);

        // Verify
        assertThat(collectedEarly).as("Not with 3 px showing").isFalse();
        assertThat(mushroom.isCollected()).as("With 4 px showing").isTrue();
    }

    @ParameterizedTest
    @EnumSource(SuperMushroomType.class)
    @DisplayName("It can never be collected from below through the block, however many times it is re-jumped")
    void fromBelowItIsNeverCollected(final SuperMushroomType variant) {
        // Prepare - a large player pressed up into the block, its box top 2 px inside the block cell: as
        // high as a bump takes it, held there for the whole rise
        final SuperMushroom mushroom = dispense(variant);
        final double boxTop = BLOCK_TOP_Y + TILE_SPRITE_SIZE - 2;
        final LevelScenePlayer player = largePlayerAt(BLOCK_X, boxTop - 5);

        // Execute & Verify
        for (int frame = 1; frame <= LAST_RISE_FRAME; frame++) {
            tick(player);
            assertThat(mushroom.isCollected()).as("Frame %d", frame).isFalse();
        }
        assertThat(mushroom.isRising()).as("The whole rise has run").isFalse();
    }

    // -------------------------------------------------------------------------
    // Spawn and rise path
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(SuperMushroomType.class)
    @DisplayName("Spawned 1 px above the block, it creeps 15 px at 1 px every 3 frames and rests on the block top")
    void itRisesFifteenPixelsOntoTheBlockTop(final SuperMushroomType variant) {
        // Prepare
        final SuperMushroom mushroom = dispense(variant);

        // Verify - the spawn pixel (prg001.asm:2282-2289)
        assertThat(mushroom.getPosition().x()).isEqualTo(BLOCK_X);
        assertThat(mushroom.getPosition().y()).as("One pixel above the cell's origin").isEqualTo(BLOCK_TOP_Y - 1);

        // Execute & Verify - every frame of the rise
        int pixelsRisen = 0;
        for (int frame = 1; frame <= LAST_RISE_FRAME; frame++) {
            final double before = mushroom.getPosition().y();
            mushroom.motionUpdate();
            final boolean creepFrame = frame >= FIRST_CREEP_FRAME
                && frame < LAST_RISE_FRAME
                && (frame - FIRST_CREEP_FRAME) % 3 == 0;
            assertThat(before - mushroom.getPosition().y())
                .as("Frame %d moves %s", frame, creepFrame ? "one pixel" : "nothing")
                .isEqualTo(creepFrame ? 1.0 : 0.0);
            if (creepFrame) {
                pixelsRisen++;
            }
            if (mushroom.isRising() && frame > HOLD_FRAMES) {
                assertThat(sliceBottomWorldY(mushroom))
                    .as("Frame %d: the visible slice sits on the block's top edge", frame)
                    .isCloseTo(blockTopWorldY(), within(TOLERANCE));
            }
        }

        // Verify
        assertThat(pixelsRisen).as("15 px of creep").isEqualTo(15);
        assertThat(mushroom.isRising()).as("The rise is over after 61 frames").isFalse();
        assertThat(mushroom.getPosition().y()).as("Feet flush on the block top").isEqualTo(BLOCK_TOP_Y - 16);
        assertThat(visiblePixels(mushroom)).as("The whole sprite shows").isEqualTo(TILE_SPRITE_SIZE);

        // Execute - the first walking frame
        mushroom.motionUpdate();

        // Verify - it lands where it already is: nothing for the ground snap to correct
        assertThat(mushroom.isGrounded()).isTrue();
        assertThat(mushroom.getPosition().y()).isEqualTo(BLOCK_TOP_Y - 16);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private SuperMushroom dispense(final SuperMushroomType variant) {
        final ImageResource image = mock(ImageResource.class);
        when(image.getDimensions()).thenReturn(new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE));
        final Texture2D texture = new Texture2D(new Image(
            RGBA8,
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE,
            createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4),
            sRGB
        ));
        when(image.asTexture()).thenReturn(texture);

        final SuperMushroom mushroom = new SuperMushroom(gameEngine, BLOCK);
        mushroom.setRewardImages(new EnumeratedImageResource<>(Map.of(
            SuperMushroomImageResource.GROWING_CAPABLE, image,
            SuperMushroomImageResource.LIFE_AWARDING_GREEN, image
        )));
        // As RewardDispensingLevelObject.onSuperMushroomDispensed does it
        mushroom.configure((SuperMushroomVariantData) variant.asLevelObjectType().getVariantData());
        manager.spawn(mushroom);
        return mushroom;
    }

    /** One engine tick of the active-object pipeline: clear, update and insert, then the player pass. */
    private void tick(final LevelScenePlayer player) {
        broadPhase.clear();
        manager.update();
        broadPhase.resolveActiveObjectCollisions(List.of(player));
    }

    private void tick(final LevelScenePlayer player, final int frames) {
        for (int frame = 0; frame < frames; frame++) {
            tick(player);
        }
    }

    /**
     * A large standing player at a sprite position, its hitbox derived exactly as
     * {@code LevelScenePlayer.getObjectCollisionBounds()} does: {@code Player_BoundBox} entry 1,
     * X {@code [+3, +13)}, Y {@code [+5, +30)}.
     */
    private static LevelScenePlayer largePlayerAt(final double x, final double y) {
        return playerWithBox(new AxisAlignedBoundingBox(x + 3, y + 5, x + 13, y + 30));
    }

    /** As {@link #largePlayerAt}, for {@code Player_BoundBox} entry 0: X {@code [+4, +12)}, Y {@code [+17, +30)}. */
    private static LevelScenePlayer smallPlayerAt(final double x, final double y) {
        return playerWithBox(new AxisAlignedBoundingBox(x + 4, y + 17, x + 12, y + 30));
    }

    private static LevelScenePlayer playerWithBox(final AxisAlignedBoundingBox box) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getObjectCollisionBounds()).thenReturn(box);
        when(player.getPlayerData()).thenReturn(new PlayerData());
        return player;
    }

    private static boolean isBlock(final int column, final int row) {
        return column == BLOCK.x() && row == BLOCK.y();
    }

    /** How many of the sprite's 16 rows the clipped quad currently draws. */
    private static int visiblePixels(final SuperMushroom mushroom) {
        final float spriteHeight = mushroom.getSpriteDimensions().height();
        final float drawnHeight = spriteHeight - quadBottom(mushroom);
        return Math.round(drawnHeight / spriteHeight * TILE_SPRITE_SIZE);
    }

    /** World Y of the bottom edge of the drawn slice: the quad's translation plus its clipped bottom. */
    private static double sliceBottomWorldY(final SuperMushroom mushroom) {
        return mushroom.getSpriteGeometry().getLocalTranslation().getY() + quadBottom(mushroom);
    }

    private static double blockTopWorldY() {
        return ROWS - BLOCK_TOP_Y / TILE_SPRITE_SIZE;
    }

    private static float quadBottom(final SuperMushroom mushroom) {
        final FloatBuffer positions = mushroom.getSpriteGeometry()
            .getMesh()
            .getFloatBuffer(VertexBuffer.Type.Position);
        return positions.get(1);
    }
}
