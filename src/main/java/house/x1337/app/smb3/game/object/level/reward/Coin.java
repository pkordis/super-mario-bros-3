package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.reward.animation.CoinAnimator;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SCALE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.COIN_FLIPPING;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_50;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

@Getter
@Prototype
@RequiredArgsConstructor
public final class Coin implements AnimatableLevelObject, RewardLevelObject {
    private final CoinAnimator coinAnimator = getBean(CoinAnimator.class);
    private final LevelObjectType type = COIN_FLIPPING;
    private final Reward rewardType = SCORE_50;
    private final boolean collidable = false;
    private final Geometry spriteGeometry = null; // Not individually attached
    private final GameEngine gameEngine;
    private final ImageResource imageResource;
    private final Offset offset;

    private Dimensions spriteDimensions;

    private boolean expired;
    private double pixelX;
    private double pixelY;

    @PostConstruct
    void init() {
        pixelX = (double) offset.x() * TILE_SPRITE_SIZE;
        pixelY = (double) offset.y() * TILE_SPRITE_SIZE;

        spriteDimensions = new Dimensions(
            "Coin",
            imageResource.getDimensions().width() * PIXELS_TO_GAME_UNITS,
            imageResource.getDimensions().height() * PIXELS_TO_GAME_UNITS
        );
    }

    @Override
    public void onCollisionWith(final LevelScenePlayer player) {
        if (expired) {
            return;
        }
        expired = true;

        final PlayerData playerData = player.getPlayerData();
        playerData.addCoin();
        playerData.addPoints(rewardType.getData().getPoints());

        coinAnimator.unregisterAt(offset);
        eraseFromBakedTexture(
            gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS),
            gameEngine.getLevelScene().getDimensions()
        );
        getCollisionGrid().removeLevelObjectAt(offset);
    }

    @Override
    public boolean isCollected() {
        return expired;
    }

    @Override
    public AxisAlignedBoundingBox getBounds() {
        final DimensionsPixels imageDimensions = imageResource.getDimensions();
        return new AxisAlignedBoundingBox(
            pixelX,
            pixelY,
            pixelX + (double) imageDimensions.width() / TILE_SCALE,
            pixelY + (double) imageDimensions.height() / TILE_SCALE
        );
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        // A coin is collected by contact, not struck by the tail attack.
    }
}
