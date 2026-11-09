package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.animator.SwitchBlockAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SWITCH_BLOCK;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;

/**
 * The P-Switch: a single-tile, solid, standable block that flips every breakable brick in the level
 * into a coin for a short while once the player presses it.
 *
 * <p>The switch is a tile-bound object rather than a moving one. It is placed by a switch-spawning
 * {@link Block} ({@code BRICK_SWITCH_BLOCK_SPAWNER} or {@code QUESTION_SWITCH_BLOCK_SPAWNER}) into the
 * cell directly above itself, never launched or bounced like a powerup.
 *
 * <p>It is solid and standable: the press test <em>rejects</em> detections coming from the player's
 * head probes, so only the feet/body probes can trigger it — the player stomps it from above.
 *
 * <p>Pressing it retires the switch — it stops animating, loses its collision box and repaints as
 * the static pressed sprite, by being replaced with a {@link PressedSwitchBlock} — shakes the screen
 * (see {@code LevelSceneVibration}) and opens the brick/coin window (see
 * {@code PowerSwitchTimeWindow}). Still to come: the music change. The "Wham!" sound is out of
 * scope while the project has no sound engine.
 */
@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public class SwitchBlock implements AnimatableLevelObject {
    private final LevelSceneVibration levelSceneVibration = getBean(LevelSceneVibration.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = getBean(PowerSwitchTimeWindow.class);
    private final SwitchBlockAnimator switchBlockAnimator = getBean(SwitchBlockAnimator.class);
    private final LevelObjectType type = SWITCH_BLOCK;
    private final GameEngine gameEngine;
    private final Coordinates offset;

    /**
     * {@code true} once this switch has been stomped. {@link #onCollisionFromAbove} is dispatched on
     * every frame the player rests on the tile, so the press has to be idempotent.
     */
    private boolean pressed;

    /**
     * Presses the switch: retires it from the animator, drops its collision box and repaints the cell
     * as the static pressed sprite, by swapping in a {@link PressedSwitchBlock}.
     *
     * @param levelScenePlayer the player who stomped it
     */
    @Override
    public void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        if (pressed) {
            return;
        }
        pressed = true;

        // Stop the shimmer first, or the animator would keep repainting the unpressed art over the
        // pressed sprite on its next frame flip.
        switchBlockAnimator.unregisterAt(offset);

        final PressedSwitchBlock pressedSwitchBlock = getBean(PressedSwitchBlock.class, gameEngine, offset);
        levelScenePlayer
            .getCollisionGrid()
            .placeLevelObjectAt(offset, pressedSwitchBlock);
        paintToBakedTexture(
            gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS),
            gameEngine.getLevelScene().getDimensions(),
            pressedSwitchBlock.getImageResource().getRgbData()
        );

        // The screen shakes straight after the pressed tile is placed.
        levelSceneVibration.powerShake();
        powerSwitchTimeWindow.activate();
        log.debug("Switch block pressed at {}x{}", offset.x(), offset.y());
    }
}
