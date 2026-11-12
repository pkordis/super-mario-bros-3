package house.x1337.app.smb3.game.sound;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * Something that can play sounds. The {@link SoundPlayer} is a singleton, served from the bean factory's cache, so
 * implementations hold no field of their own. It is extended by the most base types that play sounds:
 * {@code GameEngineAware} (the level objects, the level player, the level completion), {@code MotionManager} and
 * {@code Player}.
 */
public interface SoundPlayerAware {
    default SoundPlayer getSoundPlayer() {
        return getBean(SoundPlayer.class);
    }
}
