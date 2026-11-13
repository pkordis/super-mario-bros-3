package house.x1337.app.smb3.game.object.level.card;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimationOnlyMotionManager;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Getter
@Singleton
public final class LevelCompletionCardSpinAwayMotionManager
    implements AnimationOnlyMotionManager<LevelCompletionCardSpinAwayAnimation> {
    private final List<LevelCompletionCardSpinAwayAnimation> activeAnimations = new ArrayList<>();
    private final Class<LevelCompletionCardSpinAwayAnimation> animationType =
        LevelCompletionCardSpinAwayAnimation.class;

    @Value("classpath:/sprites/object/card/spin_away/<mushroom,flower,star>/frame_{0,2}.png")
    private AnimationImageResource animationFrames;

    @Value("classpath:/sprites/object/card/twinkle/frame_{0,1}.png")
    private AnimationImageResource twinkleFrames;

    /**
     * Starts the spin-away of a card collected on this tick, where it was.
     *
     * @param card the collected card
     */
    public void spawn(final LevelCompletionCard card) {
        final LevelCompletionCardSpinAwayAnimation animation = getBean(LevelCompletionCardSpinAwayAnimation.class);
        animation.configure(card.getGameEngine(), card.getOffset(), card.getFace(), animationFrames, twinkleFrames);
        activeAnimations.add(animation);
    }

    /**
     * Not used: a spin-away always starts from a collected card.
     */
    @Override
    public void spawn(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        throw new UnsupportedOperationException("A spin-away starts from a collected card");
    }
}
