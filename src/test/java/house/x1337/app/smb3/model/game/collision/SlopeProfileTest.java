package house.x1337.app.smb3.model.game.collision;

import house.x1337.app.smb3.enumeration.TileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_GENTLE_FALLING_HIGH;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_GENTLE_FALLING_LOW;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_GENTLE_RISING_HIGH;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_GENTLE_RISING_LOW;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_STEEP_FALLING;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_STEEP_RISING;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_FALLING_HIGH;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_FALLING_LOW;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_RISING_HIGH;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_RISING_LOW;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.STEEP_FALLING;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.STEEP_RISING;
import static java.lang.Math.min;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@TestInstance(PER_CLASS)
class SlopeProfileTest {
    Stream<Arguments> shapes() {
        return Stream.of(
            arguments(STEEP_RISING, true, true, -3, 5),
            arguments(STEEP_FALLING, false, true, 3, 5),
            arguments(GENTLE_RISING_LOW, true, false, -2, 6),
            arguments(GENTLE_RISING_HIGH, true, false, -2, 6),
            arguments(GENTLE_FALLING_HIGH, false, false, 2, 6),
            arguments(GENTLE_FALLING_LOW, false, false, 2, 6)
        );
    }

    Stream<Arguments> rampTypes() {
        return Stream.of(
            arguments(SOLID_RAMP_STEEP_RISING, STEEP_RISING),
            arguments(SOLID_RAMP_STEEP_FALLING, STEEP_FALLING),
            arguments(SOLID_RAMP_GENTLE_RISING_LOW, GENTLE_RISING_LOW),
            arguments(SOLID_RAMP_GENTLE_RISING_HIGH, GENTLE_RISING_HIGH),
            arguments(SOLID_RAMP_GENTLE_FALLING_HIGH, GENTLE_FALLING_HIGH),
            arguments(SOLID_RAMP_GENTLE_FALLING_LOW, GENTLE_FALLING_LOW)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    @DisplayName("A ramp's shape gives its direction, steepness, pull on the player and uphill walking speed")
    void aShapeGivesItsSlope(
        final SlopeProfile profile,
        final boolean risesToTheRight,
        final boolean steep,
        final int playerPull,
        final int enemyUphillWalkSpeed
    ) {
        // Execute & Verify
        assertThat(profile.risesToTheRight()).as("Direction").isEqualTo(risesToTheRight);
        assertThat(profile.isSteep()).as("Steepness").isEqualTo(steep);
        assertThat(profile.playerPull()).as("Pull, in sixteenths, signed by the downhill side").isEqualTo(playerPull);
        assertThat(profile.enemyUphillWalkSpeed()).as("Uphill walking speed").isEqualTo(enemyUphillWalkSpeed);
        assertThat(profile.isUphillFor(true)).as("Uphill to the right").isEqualTo(risesToTheRight);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rampTypes")
    @DisplayName("Each ramp tile type has its own slope shape")
    void eachRampTypeHasItsShape(
        final TileType type,
        final SlopeProfile profile
    ) {
        assertThat(type.findSlopeProfile()).contains(profile);
    }

    @Test
    @DisplayName("Only the ramp tile types have a slope, one type for each shape")
    void onlyRampTypesHaveASlope() {
        // Execute
        final List<SlopeProfile> slopes = Arrays
            .stream(TileType.values())
            .flatMap(type -> type.findSlopeProfile().stream())
            .toList();

        // Verify
        assertThat(slopes).containsExactlyInAnyOrder(SlopeProfile.values());
    }

    @ParameterizedTest
    @EnumSource(SlopeProfile.class)
    @DisplayName("A surface only ever goes one way and keeps within a pixel of the line's highest point in each column")
    void theSurfaceFollowsItsLine(final SlopeProfile profile) {
        for (int column = 0; column < TILE_SPRITE_SIZE; column++) {
            final double lineDepth = min(profile.idealSurfaceDepthAt(column), profile.idealSurfaceDepthAt(column + 1));
            assertThat((double) profile.surfaceDepthAt(column))
                .as("Column %d", column)
                .isCloseTo(lineDepth, within(1.0));
            if (column > 0) {
                final int step = profile.surfaceDepthAt(column) - profile.surfaceDepthAt(column - 1);
                assertThat(profile.risesToTheRight() ? step <= 0 : step >= 0).as("Column %d", column).isTrue();
            }
        }
    }

    @Test
    @DisplayName("A pixel is solid at or below the surface, in level pixels or within the tile")
    void solidityFollowsTheSurface() {
        assertThat(STEEP_FALLING.isSolidAt(4, 3)).as("Above the surface").isFalse();
        assertThat(STEEP_FALLING.isSolidAt(4, 4)).as("On the surface").isTrue();
        assertThat(STEEP_FALLING.isSolidAt(16 * 3 + 4, 16 * 5 + 4)).as("In level pixels").isTrue();
    }

    @Test
    @DisplayName("Behind every ramp shape is a straight line that meets the ground at its low end")
    void theIdealSurfaceIsAStraightLine() {
        // Execute & Verify - the depth at the left edge, half way and the right edge
        assertLine(STEEP_RISING, 16, 8, 0);
        assertLine(STEEP_FALLING, 0, 8, 16);
        assertLine(GENTLE_RISING_LOW, 16, 12, 8);
        assertLine(GENTLE_RISING_HIGH, 8, 4, 0);
        assertLine(GENTLE_FALLING_HIGH, 0, 4, 8);
        assertLine(GENTLE_FALLING_LOW, 8, 12, 16);
    }

    private void assertLine(
        final SlopeProfile profile,
        final double left,
        final double middle,
        final double right
    ) {
        assertThat(profile.idealSurfaceDepthAt(0)).as("Left edge").isCloseTo(left, within(1e-9));
        assertThat(profile.idealSurfaceDepthAt(8)).as("Half way").isCloseTo(middle, within(1e-9));
        assertThat(profile.idealSurfaceDepthAt(16)).as("Right edge").isCloseTo(right, within(1e-9));
    }
}
