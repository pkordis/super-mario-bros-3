package house.x1337.app.smb3.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.enumeration.SoundChannel.LEVEL_1;
import static house.x1337.app.smb3.enumeration.SoundChannel.LEVEL_2;
import static house.x1337.app.smb3.enumeration.SoundChannel.PLAYER;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.CUTS;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.LOCKS;
import static house.x1337.app.smb3.enumeration.SoundChannelRule.WAITS_FOR_SILENCE;
import static house.x1337.app.smb3.enumeration.SoundEffect.AIRSHIP_MOVEMENT;
import static house.x1337.app.smb3.enumeration.SoundEffect.BLOCK;
import static house.x1337.app.smb3.enumeration.SoundEffect.BOWSER_FIREBALL;
import static house.x1337.app.smb3.enumeration.SoundEffect.BREAKING_BLOCK;
import static house.x1337.app.smb3.enumeration.SoundEffect.CANNONBALL;
import static house.x1337.app.smb3.enumeration.SoundEffect.CASINO_BEEP;
import static house.x1337.app.smb3.enumeration.SoundEffect.COIN;
import static house.x1337.app.smb3.enumeration.SoundEffect.ENEMY_STOMP;
import static house.x1337.app.smb3.enumeration.SoundEffect.FIREBALL;
import static house.x1337.app.smb3.enumeration.SoundEffect.HAMMER_BRO_MOVEMENT;
import static house.x1337.app.smb3.enumeration.SoundEffect.JUMP;
import static house.x1337.app.smb3.enumeration.SoundEffect.KOOPA_SHELL;
import static house.x1337.app.smb3.enumeration.SoundEffect.ONE_UP;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_DOWN;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_ON;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_UP;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_UP_BLOCK;
import static house.x1337.app.smb3.enumeration.SoundEffect.TAIL_WAG;
import static house.x1337.app.smb3.enumeration.SoundEffect.TANOOKI;
import static house.x1337.app.smb3.enumeration.SoundEffect.VINE;
import static org.assertj.core.api.Assertions.assertThat;

/** Which channel each in-level sound effect plays on, and the few that claim it by a rule other than cutting. */
class SoundEffectChannelTest {
    @Test
    @DisplayName("The player's own sounds, the full P-meter's ring included, share the player channel")
    void thePlayersSoundsShareTheirChannel() {
        // Execute & Verify
        assertThat(List.of(JUMP, BLOCK, ENEMY_STOMP, KOOPA_SHELL, POWER_DOWN, FIREBALL, POWER_ON))
            .extracting(SoundEffect::getChannel)
            .containsOnly(PLAYER);
    }

    @Test
    @DisplayName("The rewards and the tail wag share one channel, the noisy sounds another")
    void theLevelSoundsShareTheirChannels() {
        // Execute & Verify
        assertThat(List.of(COIN, POWER_UP_BLOCK, VINE, CANNONBALL, CASINO_BEEP, POWER_UP, ONE_UP, TANOOKI, TAIL_WAG))
            .extracting(SoundEffect::getChannel)
            .containsOnly(LEVEL_1);
        assertThat(List.of(BREAKING_BLOCK, BOWSER_FIREBALL, AIRSHIP_MOVEMENT, HAMMER_BRO_MOVEMENT))
            .extracting(SoundEffect::getChannel)
            .containsOnly(LEVEL_2);
    }

    @Test
    @DisplayName("Only the 1-up and the airship lock, and only the tail wag waits for silence")
    void theRulesOtherThanCutting() {
        // Execute & Verify
        assertThat(withRule(LOCKS)).containsExactly(AIRSHIP_MOVEMENT, ONE_UP);
        assertThat(withRule(WAITS_FOR_SILENCE)).containsExactly(TAIL_WAG);
        assertThat(withRule(CUTS)).hasSize(SoundEffect.values().length - 3);
    }

    private List<SoundEffect> withRule(final SoundChannelRule rule) {
        return Arrays.stream(SoundEffect.values()).filter(effect -> effect.getChannelRule() == rule).toList();
    }
}
