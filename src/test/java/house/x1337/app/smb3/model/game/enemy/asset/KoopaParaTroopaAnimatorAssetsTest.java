package house.x1337.app.smb3.model.game.enemy.asset;

import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParatroopa.MIDDLE_ROW_CANVAS_TOP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the green Koopa Paratroopa's bundle. {@code Troopa_Draw} draws the walking troopa's own body for the
 * paratroopa (dasm prg004.asm:4323), so the body frames stay pixel-for-pixel copies of the troopa's. The
 * wing files are the ROM's own {@code $CD}/{@code $CF} patterns, at the rows they occupy in their sprite.
 */
class KoopaParaTroopaAnimatorAssetsTest {
    private static final String GREEN_CONTEXT = "sprites/enemy/koopa/paratroopa/green/";
    private static final String TROOPA_CONTEXT = "sprites/enemy/koopa/troopa/green/";

    private static final List<String> WALK_FILENAMES = List.of(
        "koopa_paratroopa_walk_1.png",
        "koopa_paratroopa_walk_2.png"
    );

    @Test
    @DisplayName("assets.json names the two body frames, then the raised pair, then the lowered pair")
    void theDescriptorNamesEveryFile() {
        // Prepare
        final List<String> requested = new ArrayList<>();

        // Execute
        final KoopaParaTroopaAnimatorAssets assets = AnimatorAssetsLoader.load(
            KoopaParaTroopaAnimatorAssets.class,
            GREEN_CONTEXT,
            filename -> {
                requested.add(filename);
                return null;
            }
        );

        // Verify - the flipped file first in each pair, as KoopaParatroopaAnimator.FLIPPED_WING says
        assertThat(requested).containsExactly(
            "koopa_paratroopa_walk_1.png",
            "koopa_paratroopa_walk_2.png",
            "koopa_paratroopa_wing_up_left.png",
            "koopa_paratroopa_wing_up_right.png",
            "koopa_paratroopa_wing_down_left.png",
            "koopa_paratroopa_wing_down_right.png"
        );
        assertThat(assets.walk()).hasSize(2);
        assertThat(assets.wingUp()).hasSize(2);
        assertThat(assets.wingDown()).hasSize(2);
    }

    @Test
    @DisplayName("The body frames are the walking green troopa's 16x32 frames, unchanged")
    void theBodyIsTheWalkingTroopa() throws IOException {
        for (int frame = 0; frame < WALK_FILENAMES.size(); frame++) {
            // Prepare
            final BufferedImage source = read(TROOPA_CONTEXT + "koopa_troopa_walk_%d.png".formatted(frame + 1));

            // Execute
            final BufferedImage copy = read(GREEN_CONTEXT + WALK_FILENAMES.get(frame));

            // Verify
            assertThat(copy.getWidth()).isEqualTo(TILE_SPRITE_SIZE);
            assertThat(copy.getHeight()).isEqualTo(2 * TILE_SPRITE_SIZE);
            assertSamePixels(copy, source, WALK_FILENAMES.get(frame));
        }
    }

    @Test
    @DisplayName("The wing art sits where the ROM's $CD and $CF patterns sit in their 8x16 sprite")
    void theWingArtSitsAtTheRomsRows() throws IOException {
        // Prepare - first and last rows with art, read off CHR/chr079.pcx: $CC/$CD is rows 2..14, $CE/$CF
        // is rows 7..15. The Para-Goomba's files carry the same shapes 2 and 4 rows higher, which is what
        // left a gap under the paratroopa's wing when they were reused.
        final List<String> poses = List.of("up", "down");
        final List<int[]> romRows = List.of(new int[] {2, 14}, new int[] {7, 15});

        for (int pose = 0; pose < poses.size(); pose++) {
            for (final String side : List.of("left", "right")) {
                // Execute
                final BufferedImage wing = read(GREEN_CONTEXT + "koopa_paratroopa_wing_%s_%s.png"
                    .formatted(poses.get(pose), side));

                // Verify
                assertThat(wing.getWidth()).isEqualTo(TILE_SPRITE_SIZE / 2);
                assertThat(wing.getHeight()).isEqualTo(TILE_SPRITE_SIZE);
                assertThat(firstArtRow(wing, 0, wing.getWidth())).as("%s_%s top", poses.get(pose), side)
                    .isEqualTo(romRows.get(pose)[0]);
                assertThat(lastArtRow(wing)).as("%s_%s bottom", poses.get(pose), side)
                    .isEqualTo(romRows.get(pose)[1]);
            }
        }
    }

    @Test
    @DisplayName("Each wing beat ships its SPR_HFLIP mirror as the _left file")
    void theLeftWingsMirrorTheRightOnes() throws IOException {
        for (final String pose : List.of("up", "down")) {
            // Prepare
            final BufferedImage left = read(GREEN_CONTEXT + "koopa_paratroopa_wing_%s_left.png".formatted(pose));

            // Execute
            final BufferedImage right = read(GREEN_CONTEXT + "koopa_paratroopa_wing_%s_right.png".formatted(pose));

            // Verify
            for (int row = 0; row < right.getHeight(); row++) {
                for (int column = 0; column < right.getWidth(); column++) {
                    assertThat(left.getRGB(right.getWidth() - 1 - column, row))
                        .as("%s pixel (%d, %d)", pose, column, row)
                        .isEqualTo(right.getRGB(column, row));
                }
            }
        }
    }

    @Test
    @DisplayName("The middle body row the wing hangs from starts where KoopaParatroopa says, in both frames")
    void theMiddleRowMatchesTheWalkArt() throws IOException {
        for (int frame = 0; frame < WALK_FILENAMES.size(); frame++) {
            // Prepare - the back column holds only $C4 above the feet, and $C4's first row has art
            final BufferedImage walk = read(GREEN_CONTEXT + WALK_FILENAMES.get(frame));

            // Execute
            final int middleRowTop = firstArtRow(walk, TILE_SPRITE_SIZE / 2, TILE_SPRITE_SIZE);

            // Verify
            assertThat(middleRowTop).as("walk_%d", frame + 1).isEqualTo(MIDDLE_ROW_CANVAS_TOP[frame]);
        }
    }

    private static int firstArtRow(final BufferedImage image, final int fromColumn, final int toColumn) {
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = fromColumn; column < toColumn; column++) {
                if ((image.getRGB(column, row) >>> 24) != 0) {
                    return row;
                }
            }
        }
        return -1;
    }

    private static int lastArtRow(final BufferedImage image) {
        for (int row = image.getHeight() - 1; row >= 0; row--) {
            for (int column = 0; column < image.getWidth(); column++) {
                if ((image.getRGB(column, row) >>> 24) != 0) {
                    return row;
                }
            }
        }
        return -1;
    }

    private static void assertSamePixels(final BufferedImage copy, final BufferedImage source, final String name) {
        assertThat(copy.getWidth()).isEqualTo(source.getWidth());
        assertThat(copy.getHeight()).isEqualTo(source.getHeight());
        for (int row = 0; row < source.getHeight(); row++) {
            for (int column = 0; column < source.getWidth(); column++) {
                assertThat(copy.getRGB(column, row))
                    .as("Pixel (%d, %d) of %s", column, row, name)
                    .isEqualTo(source.getRGB(column, row));
            }
        }
    }

    private BufferedImage read(final String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
