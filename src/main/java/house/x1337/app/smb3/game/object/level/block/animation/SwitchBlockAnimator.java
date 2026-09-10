package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.SwitchBlock;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SWITCH_BLOCK;

/**
 * Shimmer animator for the P-Switch ({@link SwitchBlock}).
 *
 * <p>In the ROM every animated background tile is driven by one global CHR bank cycle: the VROM page
 * at PPU $0800 is set from {@code PT2_Anim} indexed by {@code (Counter_1 & $18) >> 3}, giving four
 * phases of 8 ticks each and a 32-tick loop (dasm {@code prg030.asm PRG030_8E4F}, table at
 * {@code prg030.asm:423} — {@code .byte $60, $62, $64, $66}). That is why this animator, the question
 * block and the brick all use four frames at 8 ticks.
 *
 * <p>The P-Switch is drawn from patterns {@code $E0,$E2,$E1,$E3}
 * ({@code prg029.asm OneTile_ChangeToPatterns}, entry {@code $12 CHNGTILE_PSWITCHAPPEAR}). Those
 * patterns are byte-identical in banks {@code $62} and {@code $66}, so the switch has only <b>three</b>
 * distinct images and they play as a ping-pong <b>A-B-C-B</b>: the middle frame is on screen for 16 of
 * the 32 ticks (50%) while the outer two get 8 ticks each (25%). {@code frame_3.png} is therefore an
 * intentional duplicate of {@code frame_1.png}, mirroring the ROM's own duplicated CHR, which lets a
 * plain linear four-frame cycle reproduce the original timing exactly.
 */
@Getter
@Singleton
public final class SwitchBlockAnimator extends GameObjectAnimatorSingleTiled<SwitchBlock> {
    private final List<LevelObjectType> supportedTypes = List.of(SWITCH_BLOCK);
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/block/switch/frame_{0,3}.png")
    private AnimationImageResource animationFrames;
}
