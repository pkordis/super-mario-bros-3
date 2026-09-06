package house.x1337.app.smb3.game.object.level.reward.animation;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.reward.Coin;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * Paints the flipping-coin animation into the interactive-objects layer and, because a {@link Coin}
 * is collected on overlap, also feeds every live coin into the scene's active-object broadphase each
 * tick. Doing the broadphase insertion here — the animator already owns the coins for painting and
 * runs every tick — avoids a separate coin manager; collection itself lives entirely in
 * {@link Coin#onCollisionWith}, which unregisters the collected coin so it is neither painted nor
 * fed thereafter.
 */
@Getter
@Singleton
public final class CoinAnimator extends GameObjectAnimatorSingleTiled<Coin> {
    private final List<Class<? extends Coin>> supportedTypes = List.of(Coin.class);
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/coin/flipping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    @Override
    public void update() {
        super.update();
        for (final Coin coin : getAnimatableLevelObjects()) {
            coin.getGameEngine().getActiveObjectGrid().insert(coin);
        }
    }
}
