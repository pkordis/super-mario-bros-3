package house.x1337.app.smb3.game.object.level.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spin-away pictures, extracted from {@code CHR/chr007.pcx}: for each face, the 8 x 16 sprite pattern of
 * {@code ObjP41} (dasm prg002.asm:454-455) on the left and its mirror on the right, as {@code EndLevelCard_Draw}
 * draws it (prg002.asm:6308-6323). Every tile uses colour index 2 alone, drawn white; everything else is clear.
 */
class LevelCompletionCardSpinAwayPicturesTest {
    private static final int SIZE = 16;
    private static final int WHITE = 0xFFFCFCFC;
    private static final int FRAMES = 9;
    private static final int PICTURES_PER_FACE = 3;
    private static final String[] FACES = {"mushroom", "flower", "star"};
    private static final int[] EDGE_ON_FRAMES = {2, 5, 8};

    @Test
    @DisplayName("Every picture is 16 x 16, white or clear, and mirrored down the middle")
    void thePicturesAreWhiteAndMirrored() throws IOException {
        // Execute & Verify
        for (int frame = 0; frame < FRAMES; frame++) {
            final BufferedImage picture = picture(frame);
            assertThat(picture.getWidth()).isEqualTo(SIZE);
            assertThat(picture.getHeight()).isEqualTo(SIZE);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    final int argb = picture.getRGB(x, y);
                    assertThat(argb == WHITE || argb >>> 24 == 0).as("Frame %d at %d, %d", frame, x, y).isTrue();
                    assertThat(argb >>> 24).as("Mirrored, frame %d at %d, %d", frame, x, y)
                        .isEqualTo(picture.getRGB(SIZE - 1 - x, y) >>> 24);
                }
            }
        }
    }

    @Test
    @DisplayName("The edge-on picture ($FD) is the same for all three faces, and the six others all differ")
    void theEdgeOnPictureIsShared() throws IOException {
        // Prepare
        final Set<String> faces = new HashSet<>();

        // Execute
        for (int frame = 0; frame < FRAMES; frame++) {
            if (frame % PICTURES_PER_FACE != 2) {
                faces.add(shape(picture(frame)));
            }
        }

        // Verify
        assertThat(faces).as("Face-on and narrow, three faces").hasSize(6);
        assertThat(shape(picture(EDGE_ON_FRAMES[1]))).isEqualTo(shape(picture(EDGE_ON_FRAMES[0])));
        assertThat(shape(picture(EDGE_ON_FRAMES[2]))).isEqualTo(shape(picture(EDGE_ON_FRAMES[0])));
        assertThat(shape(picture(EDGE_ON_FRAMES[0])).replace(".", "")).as("A 2 px line, 16 rows").hasSize(32);
    }

    private static BufferedImage picture(final int frame) throws IOException {
        final String face = FACES[frame / PICTURES_PER_FACE];
        final String path = "/sprites/object/card/spin_away/" + face + "/frame_" + frame % PICTURES_PER_FACE + ".png";
        try (InputStream stream = LevelCompletionCardSpinAwayPicturesTest.class.getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return ImageIO.read(stream);
        }
    }

    private static String shape(final BufferedImage picture) {
        final StringBuilder shape = new StringBuilder();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                shape.append(picture.getRGB(x, y) >>> 24 == 0 ? '.' : '#');
            }
        }
        return shape.toString();
    }
}
