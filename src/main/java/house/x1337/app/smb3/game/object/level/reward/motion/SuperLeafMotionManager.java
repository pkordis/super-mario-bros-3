package house.x1337.app.smb3.game.object.level.reward.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
@RequiredArgsConstructor
public final class SuperLeafMotionManager implements MotionManager {
    /** The score caption is a {@code halfTileHeight} sprite — half a tile tall. */
    private static final float SCORE_CAPTION_HEIGHT = TILE_SIZE_GAME_UNITS / 2f;

    /**
     * The caption's rise animation starts {@code verticalOffsetAt(0) = -4}px below its baseline
     * (see {@code DeceleratingRisePopMotion}), so its first rendered frame sits 4px low.
     */
    private static final float SCORE_CAPTION_INITIAL_DIP = 4f / TILE_SPRITE_SIZE;

    /**
     * Upward lift applied to the caption's baseline so, on the single frame it shares with the
     * leaf, the caption box's bottom edge meets the leaf box's top edge exactly — no overlap.
     * Overlap would z-fight, since the caption inherits the leaf's depth. Combines the caption
     * height (its box extends that far below the baseline) with the animation's initial dip.
     */
    private static final float SCORE_CAPTION_ABOVE_LEAF_LIFT = SCORE_CAPTION_HEIGHT + SCORE_CAPTION_INITIAL_DIP;

    private final List<SuperLeaf> activeLeaves = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();

    @Override
    public void update() {
        // Advance existing score popups first. A caption spawned below (when a leaf is collected
        // this frame) is therefore left un-ticked until the next frame, so it renders once at its
        // spawn position — the single frame where the leaf and its "1000" caption are both visible.
        final Iterator<ScorePopupAnimation> scorePopupIterator = activeScorePopups.iterator();
        while (scorePopupIterator.hasNext()) {
            final ScorePopupAnimation scorePopup = scorePopupIterator.next();
            scorePopup.tick();
            if (scorePopup.isExpired()) {
                scorePopup.detach();
                scorePopupIterator.remove();
            }
        }

        final Iterator<SuperLeaf> iterator = activeLeaves.iterator();
        while (iterator.hasNext()) {
            final SuperLeaf leaf = iterator.next();

            // Collected last frame: the leaf has now shared exactly one rendered frame with its
            // score caption. Remove it; the caption carries on rising by itself.
            if (leaf.isCollected()) {
                leaf.detach();
                iterator.remove();
                continue;
            }

            leaf.tick();

            final LevelScenePlayer collidingPlayer = leaf.isExpired() ? null : leaf.findCollidingPlayer();
            if (collidingPlayer != null) {
                // Award and spawn the caption, but keep the leaf attached this frame so both render
                // together once; it is removed next frame by the isCollected() branch above.
                leaf.onCollisionWith(collidingPlayer);
                spawnScorePopup(leaf);
            } else if (leaf.isExpired()) {
                // Fell off the level — no reward, no caption.
                leaf.detach();
                iterator.remove();
            }
        }
    }

    public void spawnLeaf(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        activeLeaves.add(getBean(SuperLeaf.class, gameEngine, offset));
    }

    private void spawnScorePopup(final SuperLeaf leaf) {
        activeScorePopups.add(getBean(
            ScorePopupAnimation.class,
            leaf.getGameEngine(),
            leaf.getRewardScore().getData(),
            leaf.getOffset(),
            leaf.getCurrentWorldOffset().plus(0f, SCORE_CAPTION_ABOVE_LEAF_LIFT, 0f)
        ));
    }
}
