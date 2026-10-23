package house.x1337.app.smb3.model.game.player.level.asset;

import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.player.level.dimension.ShrunkDimensions.SPRITE_HEIGHT_PIXELS;
import static house.x1337.app.smb3.model.game.player.level.dimension.ShrunkDimensions.SPRITE_WIDTH_PIXELS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins small Mario's asset bundle. The small frames are the simple case on geometry — one tile square
 * throughout, canvas and body the same thing, so no per-frame quad width is needed — and the awkward
 * case on the hold cycle, which the ROM reads from a different row of the same table.
 */
class ShrunkAnimatorAssetsTest {
    private static final String FRAMES_CONTEXT = "sprites/player/mario/level/shrunk/";

    /**
     * {@code Player_HoldingFrames} for the small size (dasm prg008.asm:110):
     * {@code $2E, $2F, $2E, $2F}. Two poses padded out to four beats so it indexes off the walk-frame
     * counter exactly as the bigger bundles do. The ROM pins the airborne hold pose to {@code $2F} here
     * — entry 1, not entry 0 as it is when not small.
     */
    private static final List<String> HOLD_FILENAMES = List.of(
        "holding_1.png",
        "holding_2.png",
        "holding_1.png",
        "holding_2.png"
    );

    private static final String KICK_FILENAME = "kicking.png";
    private static final String FACE_FORWARD_FILENAME = "facing_forward.png";

    /** Every name the descriptor resolves, in the order the loader reads them. */
    private static final List<String> EXPECTED_FILENAMES = List.of(
        "still.png",
        "rapid_turn.png",
        FACE_FORWARD_FILENAME,
        "holding_1.png",
        "holding_2.png",
        "holding_1.png",
        "holding_2.png",
        "jumping.png",
        KICK_FILENAME,
        "flying.png",
        "still.png",
        "walking.png",
        "running_1.png",
        "running_2.png"
    );

    private static final int CANVAS_WIDTH = (int) SPRITE_WIDTH_PIXELS;
    private static final int CANVAS_HEIGHT = (int) SPRITE_HEIGHT_PIXELS;

    @Test
    @DisplayName("The descriptor resolves every frame of every action, in order")
    void theDescriptorResolvesEveryFrame() {
        // Prepare
        final List<String> requestedFilenames = new ArrayList<>();

        // Execute
        final ShrunkAnimatorAssets assets = AnimatorAssetsLoader.load(
            ShrunkAnimatorAssets.class,
            FRAMES_CONTEXT,
            filename -> {
                requestedFilenames.add(filename);
                return null;
            }
        );

        // Verify
        assertThat(requestedFilenames)
            .as("every name in the descriptor goes through the sprite loader")
            .containsExactlyElementsOf(EXPECTED_FILENAMES);
        assertThat(assets.hold()).as("hold cycle").hasSize(HOLD_FILENAMES.size());
        assertThat(assets.walk()).as("walk cycle").hasSize(2);
        assertThat(assets.run()).as("run cycle").hasSize(2);
    }

    @Test
    @DisplayName("The small hold cycle is four beats over two alternating poses")
    void theHoldCycleAlternatesTwoPoses() {
        // Four entries for two files, so small Mario and big Mario share one uniform cycle length and
        // the animator needs no size-dependent modulo. Shortening this to two entries would work today
        // and break the moment anything indexes hold[] with the shared walk-frame counter.
        assertThat(HOLD_FILENAMES).as("a four-beat cycle").hasSize(4);
        assertThat(HOLD_FILENAMES.get(2)).as("the third beat replays the first").isEqualTo(HOLD_FILENAMES.getFirst());
        assertThat(HOLD_FILENAMES.get(3)).as("the fourth beat replays the second").isEqualTo(HOLD_FILENAMES.get(1));
        assertThat(HOLD_FILENAMES.stream().distinct().toList())
            .as("over two distinct poses")
            .hasSize(2);
    }

    @Test
    @DisplayName("Every small frame is one tile square, so the mode's quad serves all of them")
    void everyFrameIsOneTileSquare() throws IOException {
        // Unlike the big bundles, nothing here overhangs the body: small Mario has no tail, and the small
        // kick keeps its foot inside the tile. ShrunkDimensions therefore needs no per-frame width, and
        // this test is what says so - a frame that outgrew the tile would need the animator changed.
        final List<String> newFilenames = new ArrayList<>(HOLD_FILENAMES.stream().distinct().toList());
        newFilenames.add(KICK_FILENAME);
        newFilenames.add(FACE_FORWARD_FILENAME);

        for (final String filename : newFilenames) {
            final BufferedImage frame = readFrame(filename);

            assertThat(frame.getWidth()).as("%s is one tile wide", filename).isEqualTo(CANVAS_WIDTH);
            assertThat(frame.getHeight()).as("%s is one tile tall", filename).isEqualTo(CANVAS_HEIGHT);
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private BufferedImage readFrame(final String filename) throws IOException {
        final String path = FRAMES_CONTEXT + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
