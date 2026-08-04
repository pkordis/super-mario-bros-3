package house.x1337.app.smb3.game.object.level.block.animation.management;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimationManager;
import house.x1337.app.smb3.game.object.level.block.animation.CoinPopAnimation;
import house.x1337.app.smb3.game.object.level.block.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages coin pop and score popup animations spawned when hitting ? blocks.
 *
 * <p>When a ? block containing a single coin is hit from below, two coordinated
 * animations play in sequence:
 * <ol>
 *   <li>A coin pops up and arcs back down (~40 ticks)</li>
 *   <li>When the coin expires, a "100" score popup spawns at its final position
 *       and rises for 48 ticks</li>
 * </ol>
 *
 * <h2>Reference: dasm prg007.asm</h2>
 * <ul>
 *   <li>Coin: {@code CoinPUps_DrawAndUpdate} (lines 2764-2850)</li>
 *   <li>Score spawn: {@code PRG007_AE28} — spawns score when coin YVel == 5</li>
 *   <li>Score: {@code Scores_GiveAndDraw} (lines 2110-2400)</li>
 * </ul>
 */
@Singleton
@RequiredArgsConstructor
public final class CoinRewardAnimationManager implements AnimationManager {
    private final List<CoinPopAnimation> activeCoins = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScores = new ArrayList<>();

    // Pending score popups waiting for their coin to expire
    private final List<PendingScorePopup> pendingScores = new ArrayList<>();

    /**
     * Holds information needed to spawn a score popup when its associated coin expires.
     */
    private record PendingScorePopup(
        GameEngine gameEngine,
        CoinPopAnimation coin
    ) {}

    /**
     * Advances all active coin and score animations by one game-tick.
     * Spawns score popups when their associated coins expire.
     */
    @Override
    public void update() {
        // Update coin animations and check for expired coins
        final Iterator<CoinPopAnimation> coinIterator = activeCoins.iterator();
        while (coinIterator.hasNext()) {
            final CoinPopAnimation anim = coinIterator.next();
            anim.tick();
            if (anim.isExpired()) {
                // Spawn score popup at coin's final position
                spawnScorePopupForExpiredCoin(anim);
                anim.detach();
                coinIterator.remove();
            }
        }

        // Update score popup animations
        final Iterator<ScorePopupAnimation> scoreIterator = activeScores.iterator();
        while (scoreIterator.hasNext()) {
            final ScorePopupAnimation anim = scoreIterator.next();
            anim.tick();
            if (anim.isExpired()) {
                anim.detach();
                scoreIterator.remove();
            }
        }
    }

    /**
     * Spawns a score popup at the final position of an expired coin.
     *
     * <p>Ported from dasm {@code PRG007_AE28}:
     * <pre>{@code
     *   JSR Score_FindFreeSlot
     *   LDA #$85
     *   STA Scores_Value,Y       ; 100 points
     *   LDA #$30
     *   STA Scores_Counter,Y     ; 48 frame lifetime
     *   LDA CoinPUp_Y,X
     *   CMP #192
     *   BLT use_coin_y           ; If coin Y < 192, use it
     *   LDA #$05                 ; Otherwise clamp to top
     *   STA Scores_Y,Y
     *   LDA CoinPUp_X,X
     *   SUB #$04                 ; Center score (X - 4)
     *   STA Scores_X,Y
     * }</pre>
     *
     * @param coin the coin animation that just expired
     */
    private void spawnScorePopupForExpiredCoin(final CoinPopAnimation coin) {
        // Find the pending score for this coin
        PendingScorePopup pending = null;
        final Iterator<PendingScorePopup> pendingIterator = pendingScores.iterator();
        while (pendingIterator.hasNext()) {
            final PendingScorePopup p = pendingIterator.next();
            if (p.coin() == coin) {
                pending = p;
                pendingIterator.remove();
                break;
            }
        }

        if (pending == null) {
            return; // No pending score for this coin (shouldn't happen)
        }

        final ScorePopupAnimation scoreAnim = new ScorePopupAnimation(
            pending.gameEngine(),
            coin.getOffset(),
            coin.getCurrentWorldX(),
            coin.getCurrentWorldY()
        );
        activeScores.add(scoreAnim);

        // TODO: Award 100 points to player score
        // TODO: Increment player coin counter
    }

    /**
     * Spawns a coin pop animation. The score popup will spawn automatically
     * when the coin expires (reaches terminal velocity).
     *
     * <p>This is the main entry point called when a ? block with a COIN_SINGLE
     * reward is hit from below.
     *
     * @param gameEngine the game engine
     * @param offset     the tile offset of the ? block that was hit
     */
    public void spawnCoinReward(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        // Don't spawn duplicate animations at the same tile
        for (final CoinPopAnimation existing : activeCoins) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }

        // Spawn coin animation
        final CoinPopAnimation coinAnim = new CoinPopAnimation(gameEngine, offset);
        activeCoins.add(coinAnim);

        // Register pending score popup (will spawn when coin expires)
        pendingScores.add(new PendingScorePopup(gameEngine, coinAnim));

        // TODO: Play coin sound (SND_LEVELCOIN)
    }
}
