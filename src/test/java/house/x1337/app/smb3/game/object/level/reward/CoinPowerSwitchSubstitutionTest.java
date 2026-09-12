package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.block.motion.BrickBlockBreakMotionManager;
import house.x1337.app.smb3.game.object.level.reward.animation.CoinAnimator;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.level.scene.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the coin half of the P-Switch substitution: while the window is open a {@link Coin} is a solid,
 * breakable brick rather than a collectable coin — the mirror of a breakable brick becoming a coin.
 *
 * <p>{@code PSwitch_SubstTileAndAttr} (dasm {@code prg000.asm:1599}) is bidirectional and swaps the tile
 * as it is read, so every consequence tested here follows from the substituted read: the solid attribute
 * {@code $03} instead of the coin's {@code $00}, the bump-block scan landing on {@code LATP_Brick}
 * because the fetched tile is now {@code TILEA_BRICK} ($67 = {@code TILEA_QBLOCKFLOWER} + 7), and the
 * coin branch of {@code Player_DoSpecialTiles} no longer matching {@code TILEA_COIN}.
 */
class CoinPowerSwitchSubstitutionTest {

    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Offset COIN_OFFSET = Offset.of(2, 3);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private PowerSwitchTimeWindow powerSwitchTimeWindow;
    private CoinAnimator coinAnimator;
    private BrickBlockBreakMotionManager brickBlockBreakMotionManager;
    private GameEngine gameEngine;
    private StaticEnvironmentCollisionGrid collisionGrid;

    @BeforeEach
    void prepare() {
        powerSwitchTimeWindow = new PowerSwitchTimeWindow();
        coinAnimator = mock(CoinAnimator.class);
        brickBlockBreakMotionManager = mock(BrickBlockBreakMotionManager.class);
        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        gameEngine = gameEngineMock();

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PowerSwitchTimeWindow.class))
            .thenReturn(powerSwitchTimeWindow);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(CoinAnimator.class))
            .thenReturn(coinAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(BrickBlockBreakMotionManager.class))
            .thenReturn(brickBlockBreakMotionManager);
        // Reward resolves its point value through a prototype bean the first time it is asked.
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Reward.Data.class))
            .thenReturn(new Reward.Data());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A coin is pass-through normally and solid while the P-Switch window is open")
    void coinBecomesSolidForTheWindow() {
        // Prepare
        final Coin coin = newCoin();

        // Execute & Verify
        assertFalse(coin.isCollidable(), "a coin is walked through");

        powerSwitchTimeWindow.activate();
        assertTrue(coin.isCollidable(), "a substituted coin reads as a solid brick (PostPSwitchAttr $03)");

        powerSwitchTimeWindow.reset();
        assertFalse(coin.isCollidable(), "the level grid is never rewritten, so the coin returns as-is");
    }

    @Test
    @DisplayName("A substituted coin is not collected on contact")
    void substitutedCoinIsNotCollectable() {
        // Prepare
        final Coin coin = newCoin();
        final LevelScenePlayer player = playerMock(true);
        powerSwitchTimeWindow.activate();

        // Execute
        coin.onCollisionWith(player);

        // Verify
        assertFalse(coin.isCollected(), "the coin branch of Player_DoSpecialTiles no longer matches");
        verifyNoInteractions(coinAnimator);
    }

    @Test
    @DisplayName("Small Mario hitting a substituted coin from below bounces it, leaving it intact")
    void smallPlayerBouncesSubstitutedCoin() {
        // Prepare
        final Coin coin = newCoin();
        final LevelScenePlayer player = playerMock(false);
        powerSwitchTimeWindow.activate();

        // Execute
        coin.onCollisionFromBelow(player);

        // Verify
        verify(brickBlockBreakMotionManager).spawnBounce(gameEngine, COIN_OFFSET, coinAnimator);
        verify(brickBlockBreakMotionManager, never()).spawnBreak(any(), any());
        assertTrue(coin.isCollidable(), "a bounced brick stays solid");
    }

    @Test
    @DisplayName("Large Mario hitting a substituted coin from below smashes it away for good")
    void largePlayerSmashesSubstitutedCoin() {
        // Prepare
        final Coin coin = newCoin();
        final PlayerData playerData = new PlayerData();
        final LevelScenePlayer player = playerMock(true, playerData);
        powerSwitchTimeWindow.activate();

        // Execute
        coin.onCollisionFromBelow(player);

        // Verify
        verify(brickBlockBreakMotionManager).spawnBreak(gameEngine, COIN_OFFSET);
        verify(coinAnimator).unregisterAt(COIN_OFFSET);
        verify(collisionGrid).removeLevelObjectAt(COIN_OFFSET);
        // LATP_Brick: STA Score_Earned with $01, i.e. 10 points.
        assertEquals(10, playerData.getScore());

        // CHNGTILE_DELETETOBG writes the background tile into the level's own tile map, so the coin does
        // not return when the window closes.
        powerSwitchTimeWindow.reset();
        assertTrue(coin.isCollected());
        assertFalse(coin.isCollidable());
    }

    @Test
    @DisplayName("Outside the window a coin ignores hits from below and the tail entirely")
    void unsubstitutedCoinIgnoresBrickHits() {
        // Prepare
        final Coin coin = newCoin();
        final LevelScenePlayer player = playerMock(true);

        // Execute
        coin.onCollisionFromBelow(player);
        coin.onTailAttack(player);

        // Verify
        verifyNoInteractions(brickBlockBreakMotionManager);
        verifyNoInteractions(coinAnimator);
        assertFalse(coin.isCollected());
    }

    @Test
    @DisplayName("The tail smashes a substituted coin regardless of the player's size")
    void tailSmashesSubstitutedCoinWhenSmall() {
        // Prepare
        final Coin coin = newCoin();
        final LevelScenePlayer player = playerMock(false);
        powerSwitchTimeWindow.activate();

        // Execute
        coin.onTailAttack(player);

        // Verify - dasm prg008.asm:5233, CPX #$04 / BEQ PRG008_B84E busts before the suit test.
        verify(brickBlockBreakMotionManager).spawnBreak(gameEngine, COIN_OFFSET);
        assertTrue(coin.isCollected());
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Coin newCoin() {
        final ImageResource imageResource = mock(ImageResource.class);
        when(imageResource.getDimensions()).thenReturn(new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE));

        final Coin coin = new Coin(gameEngine, imageResource, COIN_OFFSET);
        coin.init();
        return coin;
    }

    private GameEngine gameEngineMock() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        // Built up front: stubbing one mock inside another's thenReturn(...) argument leaves Mockito with
        // an unfinished stubbing.
        final Geometry bakedLayerGeometry = bakedLayerGeometry();

        final GameEngine engine = mock(GameEngine.class);
        when(engine.getLevelScene()).thenReturn(levelScene);
        when(engine.getCollisionGrid()).thenReturn(collisionGrid);
        when(engine.getLayerGeometry(INTERACTIVE_OBJECTS)).thenReturn(bakedLayerGeometry);
        return engine;
    }

    /**
     * A jme3 geometry whose material exposes a "ColorMap" texture backed by a real, level-sized byte
     * buffer, so {@code eraseFromBakedTexture} writes into it for real instead of being stubbed out.
     * Everything below the material is a genuine object because {@code Image.getData} and
     * {@code MatParamTexture.getTextureValue} cannot be intercepted.
     */
    private Geometry bakedLayerGeometry() {
        final int width = COLUMNS * TILE_SPRITE_SIZE;
        final int height = ROWS * TILE_SPRITE_SIZE;
        final Image image = new Image(
            RGBA8,
            width,
            height,
            createByteBuffer(width * height * 4),
            sRGB
        );
        final MatParamTexture textureParam = new MatParamTexture(
            VarType.Texture2D,
            "ColorMap",
            new Texture2D(image),
            sRGB
        );

        final Material material = mock(Material.class);
        when(material.getTextureParam(anyString())).thenReturn(textureParam);

        final Geometry geometry = mock(Geometry.class);
        when(geometry.getMaterial()).thenReturn(material);
        return geometry;
    }

    private LevelScenePlayer playerMock(final boolean large) {
        return playerMock(large, new PlayerData());
    }

    private LevelScenePlayer playerMock(
        final boolean large,
        final PlayerData playerData
    ) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.isLarge()).thenReturn(large);
        when(player.getPlayerData()).thenReturn(playerData);
        when(player.getCollisionGrid()).thenReturn(collisionGrid);
        return player;
    }
}
