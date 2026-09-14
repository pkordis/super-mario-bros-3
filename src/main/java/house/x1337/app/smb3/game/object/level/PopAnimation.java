package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.motion.pop.PopMotion;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.model.game.WorldOffset.of;

@Getter
@RequiredArgsConstructor
public abstract class PopAnimation implements GameEngineAware, GameRenderer {
    private final GameEngine gameEngine;
    private final Reward.Data rewardData;
    private final Offset offset;

    private WorldOffset worldOffset;

    public abstract Dimensions getDimensions();
    public abstract PopMotion getMotion();

    public WorldOffset adjustWorldOffset(
        final WorldOffset worldOffset,
        final Offset offset
    ) {
        // Do not adjust if not overridden
        return worldOffset;
    }

    @Setter
    private boolean expired;
    private int frameIndex = 0;
    private Geometry spriteGeometry;

    public void setWorldOffset(final WorldOffset worldOffset) {
        this.worldOffset = adjustWorldOffset(worldOffset, offset);
    }

    public void start() {
        spriteGeometry = createAndAttachSprite(textureForSpriteGeometry(), getDimensions());
        positionSprite();
    }

    public WorldOffset getCurrentWorldOffset() {
        return of(
            worldOffset.x(),
            calculateWorldY(),
            Z_DEPTH_ITEM_REWARD
        );
    }

    public void tick() {
        if (isExpired()) {
            return;
        }
        ++frameIndex;

        if (frameIndex >= getMotion().durationTicks()) {
            setExpired(true);
            return;
        }

        onFrameAdvanced(spriteGeometry, frameIndex);
        positionSprite();
    }

    public void onFrameAdvanced(final Geometry spriteGeometry, final int frameIndex) {
        // Default: no action
    }

    public void detach() {
        getRootNode().detachChild(getSpriteGeometry());
    }

    public void positionSprite() {
        final float worldY = calculateWorldY() - getDimensions().height();
        getSpriteGeometry().setLocalTranslation(worldOffset.x(), worldY, worldOffset.z());
    }

    public float calculateWorldY() {
        final int yOffset = getMotion().verticalOffsetAt(getFrameIndex());
        return worldOffset.y() + (float) yOffset / TILE_SPRITE_SIZE;
    }

    public Geometry createAndAttachSprite(final Texture texture, final Dimensions dimensions) {
        final Geometry geometry = fromTexture(getAssetManager(), texture, dimensions);
        getRootNode().attachChild(geometry);
        return geometry;
    }

    public abstract Texture textureForSpriteGeometry();

    private Node getRootNode() {
        return getGameEngine().getRootNode();
    }
}
