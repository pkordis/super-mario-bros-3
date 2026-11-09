package house.x1337.app.smb3.game.object.level.reward.animation;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.reward.Coin;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.ImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.COIN_FLIPPING;

/**
 * Paints the flipping-coin animation into the interactive-objects layer and, because a {@link Coin}
 * is collected on overlap, also feeds every live coin into the scene's active-object broadphase each
 * tick. Doing the broadphase insertion here — the animator already owns the coins for painting and
 * runs every tick — avoids a separate coin manager; collection itself lives entirely in
 * {@link Coin#onCollisionWith}, which unregisters the collected coin so it is neither painted nor
 * fed thereafter.
 *
 * <p>While a P-Switch window is open this paints a static brick instead, the mirror image of
 * {@code BrickBlockAnimator} painting a coin: the P-Switch substitution is bidirectional, turning
 * coins into bricks just as it turns bricks into coins. The brick is a single frame because a brick
 * does not shimmer — only the coin does — so the coin's four-frame cycle flattens to one image for
 * the duration.
 */
@Getter
@Singleton
public final class CoinAnimator extends GameObjectAnimatorSingleTiled<Coin> {
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = getBean(PowerSwitchTimeWindow.class);
    private final List<LevelObjectType> supportedTypes = List.of(COIN_FLIPPING);
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/coin/flipping/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    @Value("classpath:/sprites/object/brick/plain/frame_0.png")
    private ImageResource powerSwitchedBrickImageResource;

    private boolean substituting;

    @Override
    public void update() {
        // Repaint the moment the window opens or closes rather than waiting up to 8 ticks for the next
        // frame flip; the substitution is meant to be instantaneous.
        if (substituting != powerSwitchTimeWindow.isActive()) {
            substituting = powerSwitchTimeWindow.isActive();
            repaintCurrentFrame();
        }
        super.update();

        // A substituted coin is a solid brick, hit through the terrain grid like any other; it is not a
        // collectable object, so it stays out of the overlap broadphase until the window closes.
        if (substituting) {
            return;
        }
        for (final Coin coin : getAnimatableLevelObjects()) {
            coin.getGameEngine().getActiveObjectGrid().insert(coin);
        }
    }

    @Override
    protected int[] getFramePixels(final int frameIndex) {
        return powerSwitchTimeWindow.isActive()
            ? powerSwitchedBrickImageResource.getRgbData()
            : super.getFramePixels(frameIndex);
    }
}
