package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.level.animator.StarSpritePalette;
import house.x1337.app.smb3.model.event.StarInvincibilityMusicEnded;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.STAR_MUSIC_END_COUNT;

public interface LevelScenePlayerStarInvincibilityCapable
    extends
        GameEngineAware,
        LevelScenePlayerRenderer,
        LevelScenePlayerRuntimeStateAware {
    int STAR_PALETTE_MASK = 3;
    int STAR_PALETTE_PHASE_MASK = 15;
    int STAR_SLOW_PALETTE_SHIFT = 2;

    /**
     * @return which of the four palettes the player is drawn in this frame: 0 is the normal one. A new one every
     *         frame while the counter is {@value PlayerRuntimeState#STAR_MUSIC_END_COUNT} or more, then one every
     *         four frames.
     */
    default int starPalette() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (!runtimeState.isStarInvincible()) {
            return 0;
        }
        final int phase = getGameEngine().getLevelFrameCounter().getTicks() & STAR_PALETTE_PHASE_MASK;
        final boolean wearingOff = runtimeState.getStarInvincibilityCounter() < STAR_MUSIC_END_COUNT;
        return (wearingOff ? phase >> STAR_SLOW_PALETTE_SHIFT : phase) & STAR_PALETTE_MASK;
    }

    /** Draws the player's sprites in this frame's star palette, and back in their own once it is over. */
    default void applyStarPalette() {
        getNode();
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (getNode() == null || !runtimeState.isStarInvincible() && !runtimeState.isStarPaletteShown()) {
            return;
        }
        final int palette = starPalette();
        getBean(StarSpritePalette.class).applyTo(getNode(), palette);
        runtimeState.setStarPaletteShown(palette != 0);
    }

    /**
     * Counts the star invincibility down on every second frame and ends the air flip once the player has landed.
     * The level's music takes over again on the frame the counter reaches
     * {@value PlayerRuntimeState#STAR_MUSIC_END_COUNT}.
     */
    default void tickStarInvincibilityCountdown() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (!runtimeState.isInAir()) {
            runtimeState.setAirFlipping(false);
        }
        if (!runtimeState.isStarInvincible() || (getGameEngine().getLevelFrameCounter().getTicks() & 1) == 0) {
            return;
        }
        runtimeState.decrementStarInvincibility();
        if (runtimeState.getStarInvincibilityCounter() == STAR_MUSIC_END_COUNT) {
            getGameEngine().publish(new StarInvincibilityMusicEnded());
        }
    }
}
