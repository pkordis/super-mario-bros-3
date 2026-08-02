package house.x1337.app.smb3.game.object.level.brick;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.collision.CollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.brick.animation.management.BrickBlockAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.BRICK_BLOCK_WITH_REWARD;

@Getter
@Prototype
@RequiredArgsConstructor
public class BrickBlockWithReward implements BrickBlock {
    private final LevelObjectType type = BRICK_BLOCK_WITH_REWARD;
    private final BrickBlockAnimator brickBlockAnimator = getBean(BrickBlockAnimator.class);
    private final Offset offset;

    public void triggerReward(final GameEngine gameEngine) {
    }

    /**
     * Zeroes the 16×16 RGBA pixel region for a tile in the baked
     * {@code "Layer-INTERACTIVE_OBJECTS"} texture and signals jme3 to re-upload it.
     *
     * <p>jme3 image row 0 is the bottom of the image (= bottom of the level = highest
     * tile-row index). The Y-flip formula is:
     * <pre>imgRow = (totalRows − 1 − tileRow) × TILE_SPRITE_SIZE + (TILE_SPRITE_SIZE − 1 − sprPixelRow)</pre>
     */
    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        final CollisionGrid collisionGrid = levelScenePlayer.getCollisionGrid();
        final GameEngine gameEngine = levelScenePlayer.getGameEngine();
        if (levelScenePlayer.isLarge()) {

        } else {

        }
    }
}
