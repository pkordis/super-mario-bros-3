package house.x1337.app.smb3.game.object.level.reward.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * Lifecycle manager for {@link SuperLeaf}s and their score captions. It no longer performs
 * collision itself: the engine owns the scene-wide broadphase and runs one player↔object pass. This
 * manager only (a) ticks and despawns leaves and feeds the live ones into the shared grid during
 * {@link #update()}, and (b) reacts to collisions the engine detected this tick in
 * {@link #postCollision()} by spawning the "1000" caption — on the same frame, which is what keeps
 * the leaf and caption visible together for one frame before the leaf vanishes.
 */
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

    /**
     * How far beyond the visible viewport a leaf keeps simulating, in sprite-pixels. Two tiles of
     * slack keeps a leaf live just off-screen; leaves are always near the player in practice, so
     * this mainly exercises the activation path for the enemies that will share it later.
     */
    private static final int ACTIVATION_MARGIN_PIXELS = TILE_SPRITE_SIZE * 2;

    private final List<SuperLeaf> activeLeaves = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();

    @Override
    public void update() {
        // Advance existing score popups first. A caption spawned later this tick (in postCollision)
        // is therefore left un-ticked until the next tick, so it renders once at its spawn position
        // — the single frame where the leaf and its "1000" caption are both visible.
        tickScorePopups();

        if (activeLeaves.isEmpty()) {
            return;
        }

        // Every active leaf belongs to the running scene, so any of them yields its GameEngine.
        final GameEngine gameEngine = activeLeaves.get(0).getGameEngine();
        final AxisAlignedBoundingBox activeRegion = gameEngine.getCameraState().getActiveObjectRegion(ACTIVATION_MARGIN_PIXELS);
        final ActiveObjectGrid<ActiveLevelObject> broadphase = gameEngine.getActiveObjectGrid();

        final Iterator<SuperLeaf> iterator = activeLeaves.iterator();
        while (iterator.hasNext()) {
            final SuperLeaf leaf = iterator.next();

            // Collected last tick: the leaf has now shared its one rendered frame with the caption.
            // Remove it; the caption carries on rising by itself.
            if (leaf.isCollected()) {
                leaf.detach();
                iterator.remove();
                continue;
            }

            final boolean active = activeRegion.intersects(leaf.getBounds());
            if (active) {
                leaf.tick();
            }

            if (leaf.isExpired()) {
                // Fell off the level — no reward, no caption.
                leaf.detach();
                iterator.remove();
                continue;
            }

            if (active) {
                // Feed the shared broadphase; the engine's collision pass queries it after every
                // manager has inserted. Off-screen leaves stay out — they cannot hit an on-screen
                // player.
                broadphase.insert(leaf);
            }
        }
    }

    @Override
    public void postCollision() {
        // The engine's collision pass has just run; a leaf collected this tick is flagged but not
        // yet removed (update() removes it next tick). Spawn its caption now so both render together
        // for one frame. Each collected leaf is seen exactly once here — it is gone by the next tick.
        for (final SuperLeaf leaf : activeLeaves) {
            if (leaf.isCollected()) {
                spawnScorePopupFor(leaf);
            }
        }
    }

    private void tickScorePopups() {
        final Iterator<ScorePopupAnimation> scorePopupIterator = activeScorePopups.iterator();
        while (scorePopupIterator.hasNext()) {
            final ScorePopupAnimation scorePopup = scorePopupIterator.next();
            scorePopup.tick();
            if (scorePopup.isExpired()) {
                scorePopup.detach();
                scorePopupIterator.remove();
            }
        }
    }

    public void spawnLeaf(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        activeLeaves.add(getBean(SuperLeaf.class, gameEngine, offset));
    }

    private void spawnScorePopupFor(final SuperLeaf leaf) {
        activeScorePopups.add(getBean(
            ScorePopupAnimation.class,
            leaf.getGameEngine(),
            leaf.getRewardScore().getData(),
            leaf.getOffset(),
            leaf.getCurrentWorldOffset().plus(0f, SCORE_CAPTION_ABOVE_LEAF_LIFT, 0f)
        ));
    }
}
