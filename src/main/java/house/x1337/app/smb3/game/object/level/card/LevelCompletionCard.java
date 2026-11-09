package house.x1337.app.smb3.game.object.level.card;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.enumeration.resource.LevelCompletionCardImageResource;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.VirtualLevelObjectType.LEVEL_COMPLETION_CARD;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUNDING_BOX_HEIGHT;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUNDING_BOX_LEFT;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUNDING_BOX_TOP;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUNDING_BOX_WIDTH;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

@Getter
@Prototype
@RequiredArgsConstructor
public final class LevelCompletionCard implements ActiveLevelObject, GameRenderer {
    static final int ROTATION_MASK = 7;

    @Setter
    @Value("house.x1337.app.smb3.enumeration.resource.LevelCompletionCardImageResource")
    private EnumeratedImageResource<LevelCompletionCardImageResource> cardImages;

    private final GameEngine gameEngine;

    private LevelCompletionCardType face = MUSHROOM;
    private boolean collected;
    private boolean placedThisTick = true;
    private Coordinates offset;
    private Dimensions spriteDimensions;

    @Setter
    private Geometry spriteGeometry;

    public void spawnIntoScene(final Coordinates cell) {
        offset = cell;
        spriteDimensions = new Dimensions(
            "LevelCompletionCard",
            TILE_SPRITE_SIZE * PIXELS_TO_GAME_UNITS,
            TILE_SPRITE_SIZE * PIXELS_TO_GAME_UNITS
        );
        spriteGeometry = fromTexture(gameEngine.getAssetManager(), textureOf(face), spriteDimensions);
        spriteGeometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        spriteGeometry.setLocalTranslation(offset.x(), (float) (rows - 1 - offset.y()), Z_DEPTH_ITEM_REWARD);
        gameEngine.getRootNode().attachChild(spriteGeometry);
    }

    public void advance(final int frameCounter) {
        if (collected) {
            return;
        }
        if (placedThisTick) {
            placedThisTick = false;
            return;
        }
        if ((frameCounter & ROTATION_MASK) == 0) {
            face = LevelCompletionCardType.values()[(face.ordinal() + 1) % LevelCompletionCardType.values().length];
            showFace();
        }
    }

    @Override
    public void onCollisionWith(final LevelScenePlayer player) {
        if (collected) {
            return;
        }
        collected = true;
        gameEngine.getLevelCompletion().collect(face, player);
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        // Deliberately nothing.
    }

    @Override
    public boolean isKilledByRammedObject() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public LevelObjectType getType() {
        return LEVEL_COMPLETION_CARD;
    }

    @Override
    public PixelPosition getPosition() {
        return PixelPosition.ofTile(offset);
    }

    @Override
    public FixedPointVelocity getVelocity() {
        return STILL;
    }

    @Override
    public AxisAlignedBoundingBox getBounds() {
        final PixelPosition position = getPosition();
        final double inclusiveLeft = position.x() + POWER_UP_BOUNDING_BOX_LEFT;
        final double inclusiveTop = position.y() + POWER_UP_BOUNDING_BOX_TOP;
        return new AxisAlignedBoundingBox(
            inclusiveLeft - 1,
            inclusiveTop - 1,
            inclusiveLeft + POWER_UP_BOUNDING_BOX_WIDTH + 1,
            inclusiveTop + POWER_UP_BOUNDING_BOX_HEIGHT + 1
        );
    }

    @Override
    public void detach() {
        if (spriteGeometry != null) {
            gameEngine.getRootNode().detachChild(spriteGeometry);
        }
    }

    private void showFace() {
        if (spriteGeometry != null) {
            spriteGeometry.getMaterial().setTexture("ColorMap", textureOf(face));
        }
    }

    private Texture textureOf(final LevelCompletionCardType card) {
        return cardImages.getTextureFor(card.asResource());
    }
}
