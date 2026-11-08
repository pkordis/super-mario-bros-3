package house.x1337.app.smb3.game.object.level.card;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.game.object.level.ActiveLevelObjectMotionManager.SPAWN_LOOK_AHEAD_LEFT_PIXELS;
import static house.x1337.app.smb3.game.object.level.ActiveLevelObjectMotionManager.SPAWN_LOOK_AHEAD_RIGHT_PIXELS;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;

@Getter
@Prototype
@RequiredArgsConstructor
public final class LevelCompletionCardSpawner {
    private final GameEngine gameEngine;

    private Coordinates cell;
    private boolean spawned;

    public void reset() {
        final Coordinates placed = gameEngine.getLevelScene().getLevelCompletionCard();
        cell = placed == null || placed.x() < 0 || placed.y() < 0 ? null : placed;
        spawned = false;
    }

    public void spawnComingIntoView() {
        if (cell == null || spawned) {
            return;
        }
        final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
        final int firstColumn = floorDiv((int) floor(screen.left()) - SPAWN_LOOK_AHEAD_LEFT_PIXELS, TILE_SPRITE_SIZE);
        final int lastColumn = floorDiv((int) floor(screen.right()) + SPAWN_LOOK_AHEAD_RIGHT_PIXELS, TILE_SPRITE_SIZE);
        if (cell.x() < firstColumn || cell.x() > lastColumn) {
            return;
        }
        final LevelCompletionCard card = getBean(LevelCompletionCard.class, gameEngine);
        card.spawnIntoScene(cell);
        getBean(LevelCompletionCardMotionManager.class).spawn(card);
        spawned = true;
    }

    public void release() {
        spawned = false;
    }

    public void retire() {
        cell = null;
    }
}
