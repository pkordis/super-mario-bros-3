package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.animation.SwitchBlockAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SWITCH_BLOCK;
import static house.x1337.app.smb3.game.level.scene.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

/**
 * The P-Switch: a single-tile, solid, standable block that flips every breakable brick in the level
 * into a coin for a short while once the player presses it.
 *
 * <p>In the ROM this is a plain animated tile ({@code TILEA_PSWITCH} = $F2), not an object. It is
 * placed by a {@link Block} of type {@code SWITCH_BLOCK_SPAWNER} into the cell directly above
 * itself (dasm {@code prg008.asm LATP_PSwitch}), never launched or bounced like a powerup.
 *
 * <p>It is solid and standable: the press test in dasm {@code prg008.asm PRG008_B623} runs
 * {@code CPX #$02 / BGS} to <em>reject</em> detections coming from the player's head probes, so
 * only the feet/body probes can trigger it — the player stomps it from above.
 *
 * <p>Pressing it retires the switch — it stops animating, loses its collision box and repaints as
 * the static pressed sprite, by being replaced with a {@link PressedSwitchBlock} (dasm
 * {@code CHNGTILE_PSWITCHSTOMP} = $09 → {@code TILEA_PSWITCH_PRESSED} = $D7) — shakes the screen
 * ({@code Level_Vibration = $10}, see {@code LevelSceneVibration}) and opens the brick/coin window
 * ({@code Level_PSwitchCnt = $80}, see {@code PowerSwitchTimeWindow}). Still to come from the same ROM
 * routine: the music change ({@code MUS2B_PSWITCH}). The "Wham!" ({@code SND_LEVELBABOOM}) is out of
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
    private final Offset offset;

    /**
     * {@code true} once this switch has been stomped. {@link #onCollisionFromAbove} is dispatched on
     * every frame the player rests on the tile, so the press has to be idempotent; the ROM guards the
     * same way, skipping when {@code CHNGTILE_PSWITCHSTOMP} is already queued in
     * {@code Level_ChgTileEvent}.
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

        // dasm prg008.asm:4767 - LDA #$10 / STA Level_Vibration, straight after queueing the
        // pressed tile.
        levelSceneVibration.powerShake();
        powerSwitchTimeWindow.activate();
        log.debug("Switch block pressed at {}x{}", offset.x(), offset.y());
    }
}
