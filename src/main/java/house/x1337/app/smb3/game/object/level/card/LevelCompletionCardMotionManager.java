package house.x1337.app.smb3.game.object.level.card;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObjectMotionManager;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Getter
@Singleton
public final class LevelCompletionCardMotionManager implements ActiveLevelObjectMotionManager<LevelCompletionCard> {
    private final Class<LevelCompletionCard> type = LevelCompletionCard.class;
    private final List<LevelCompletionCard> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();

    public void spawn(final LevelCompletionCard card) {
        activeInstances.add(card);
    }

    @Override
    public void update() {
        final Iterator<LevelCompletionCard> iterator = activeInstances.iterator();
        while (iterator.hasNext()) {
            final LevelCompletionCard card = iterator.next();
            final GameEngine gameEngine = card.getGameEngine();
            card.advance(gameEngine.getLevelFrameCounter().getTicks());

            final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
            if (isPastTheDeletionEdge(card, screen)) {
                card.detach();
                iterator.remove();
                gameEngine.getLevelCompletion().getCardSpawner().release();
                continue;
            }
            gameEngine.getActiveObjectGrid().insert(card);
        }
    }

    /**
     * A card collected on this tick is taken out of the scene, and its spin-away takes its place
     * (the spin-away's first picture is drawn on the touch frame itself).
     */
    @Override
    public void postCollision() {
        final Iterator<LevelCompletionCard> iterator = activeInstances.iterator();
        while (iterator.hasNext()) {
            final LevelCompletionCard card = iterator.next();
            if (card.isCollected()) {
                card.detach();
                iterator.remove();
                spinAway(card);
            }
        }
    }

    @Override
    public void reset() {
        activeInstances.forEach(LevelCompletionCard::detach);
        activeInstances.clear();
    }

    private static void spinAway(final LevelCompletionCard card) {
        card.getGameEngine().getMotionManagers().stream()
            .filter(LevelCompletionCardSpinAwayMotionManager.class::isInstance)
            .map(LevelCompletionCardSpinAwayMotionManager.class::cast)
            .forEach(spinAways -> spinAways.spawn(card));
    }
}
