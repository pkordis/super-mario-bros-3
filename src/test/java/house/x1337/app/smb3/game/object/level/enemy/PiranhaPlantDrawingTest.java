package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.object.level.enemy.animator.PiranhaPlantAnimator;
import house.x1337.app.smb3.model.game.enemy.asset.PiranhaPlantAnimatorAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;
import static com.jme3.scene.Spatial.CullHint.Always;
import static com.jme3.scene.Spatial.CullHint.Inherit;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_PIPE_PLANT;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.PLANT_X;
import static house.x1337.app.smb3.game.object.level.enemy.PiranhaPlantTestWorld.ROWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Where the plant's two quads go. They are drawn in the baked layers' {@code Transparent} bucket at
 * {@code Z_DEPTH_PIPE_PLANT}, so the pipe layer covers the part inside the pipe, and nothing is drawn in
 * state 0.
 */
class PiranhaPlantDrawingTest {
    private static final float TOLERANCE = 1e-5f;

    private PiranhaPlantTestWorld world;

    @BeforeEach
    void prepare() {
        world = new PiranhaPlantTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    @Test
    @DisplayName("A hidden plant draws nothing")
    void aHiddenPlantDrawsNothing() {
        // Prepare & Execute
        final PiranhaPlant plant = world.spawnedPlant(GREEN);

        // Verify
        assertThat(plant.getSpriteGeometry().getLocalCullHint()).isEqualTo(Always);
        assertThat(plant.getStemGeometry().getLocalCullHint()).isEqualTo(Always);
    }

    @Test
    @DisplayName("Once it leaves state 0 the head is drawn over the stem, at the plant's X")
    void theHeadSitsOverTheStem() {
        // Prepare
        final PiranhaPlant plant = world.spawnedPlant(GREEN);

        // Execute
        world.tick(plant, 10);

        // Verify
        final Geometry head = plant.getSpriteGeometry();
        final Geometry stem = plant.getStemGeometry();
        final float expectedHeadBottom = (float) (ROWS - (plant.getPosition().y() + TILE_SPRITE_SIZE)
            / TILE_SPRITE_SIZE - 1.0 / TILE_SPRITE_SIZE);
        assertThat(head.getLocalCullHint()).isEqualTo(Inherit);
        assertThat(stem.getLocalCullHint()).isEqualTo(Inherit);
        assertThat(head.getLocalTranslation().x).isCloseTo((float) (PLANT_X / TILE_SPRITE_SIZE), within(TOLERANCE));
        assertThat(stem.getLocalTranslation().x).isCloseTo(head.getLocalTranslation().x, within(TOLERANCE));
        assertThat(head.getLocalTranslation().y).isCloseTo(expectedHeadBottom, within(TOLERANCE));
        assertThat(head.getLocalTranslation().y - stem.getLocalTranslation().y)
            .as("One 16 px quad apart")
            .isCloseTo(1f, within(TOLERANCE));
    }

    @Test
    @DisplayName("Both quads are drawn behind the pipe layer and in front of the land decorations")
    void bothQuadsAreBehindThePipe() {
        // Prepare
        final PiranhaPlant plant = world.spawnedPlant(GREEN);

        // Execute
        world.tick(plant, 10);

        // Verify
        assertThat(Z_DEPTH_PIPE_PLANT)
            .isGreaterThan(DECORATIONS_LAND.getZ())
            .isLessThan(STATIC_ENVIRONMENT.getZ());
        assertThat(plant.getSpriteGeometry().getLocalTranslation().z).isEqualTo(Z_DEPTH_PIPE_PLANT);
        assertThat(plant.getStemGeometry().getLocalTranslation().z).isEqualTo(Z_DEPTH_PIPE_PLANT);
    }

    @Test
    @DisplayName("The animator puts the head and the stem in the baked layers' Transparent bucket")
    void theAnimatorUsesTheLayersBucket() {
        // Prepare - a real animator over stand-in quads and textures
        final PiranhaPlantAnimator animator = spy(new PiranhaPlantAnimator());
        final Texture[] heads = {mock(Texture.class), mock(Texture.class)};
        doReturn(new PiranhaPlantAnimatorAssets(heads, mock(Texture.class))).when(animator).assetsFor(any());
        doReturn(standInQuad(), standInQuad()).when(animator).fromTexture(any(), any(), any());
        final PiranhaPlant plant = world.plant(GREEN);
        doReturn(mock(AssetManager.class)).when(world.getGameEngine()).getAssetManager();

        // Execute
        final Geometry head = animator.buildSprite(plant);
        final Geometry stem = animator.buildStemSprite(plant);

        // Verify
        assertThat(head.getQueueBucket()).isEqualTo(Transparent);
        assertThat(stem.getQueueBucket()).isEqualTo(Transparent);
        assertThat(animator.frameTexture(plant)).isSameAs(heads[0]);
    }

    private static Geometry standInQuad() {
        final Geometry geometry = new Geometry("Quad", new Quad(1f, 1f));
        geometry.setMaterial(mock(Material.class, RETURNS_DEEP_STUBS));
        return geometry;
    }
}
