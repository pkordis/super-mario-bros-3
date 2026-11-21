package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.model.game.MultiTileArea;

import javax.swing.Timer;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/**
 * Blinks a black and yellow striped border around the objects that blocked a placement. During one press and drag,
 * each object blinks at most once, and an object placed in the same gesture never does. See kb/editor.md,
 * "Overlapping objects are refused".
 */
final class BlockerFlash implements GridBoxDrawing {
    static final int PHASE_MILLIS = 250;
    static final int DURATION_MILLIS = 2000;
    private static final int STRIPE_WIDTH = 4;
    private static final int STRIPE_BLACK = 0xFF000000;
    private static final int STRIPE_YELLOW = 0xFFFFD800;

    private final Set<MultiTileArea> flashing = new LinkedHashSet<>();
    private final Set<MultiTileArea> settledThisGesture = new HashSet<>();
    private final TexturePaint stripes = stripes();
    private final Consumer<Rectangle> repainter;
    private final LongSupplier clock;
    private final Timer timer;
    private long startedAt;

    BlockerFlash(final Consumer<Rectangle> repainter, final LongSupplier clock) {
        this.repainter = repainter;
        this.clock = clock;
        timer = new Timer(PHASE_MILLIS, event -> update());
    }

    void beginGesture() {
        settledThisGesture.clear();
    }

    void notePlaced(final MultiTileArea placed) {
        settledThisGesture.add(placed);
    }

    /** Blinks the blockers not yet refused or placed in this gesture, restarting the blink. */
    void refuse(final Collection<MultiTileArea> blockers) {
        boolean anyNew = false;
        for (final MultiTileArea blocker : blockers) {
            if (settledThisGesture.add(blocker)) {
                flashing.add(blocker);
                anyNew = true;
            }
        }
        if (!anyNew) {
            return;
        }
        startedAt = clock.getAsLong();
        timer.restart();
        repaintFlashing();
    }

    Set<MultiTileArea> getFlashing() {
        return Set.copyOf(flashing);
    }

    boolean isOn() {
        final long elapsed = clock.getAsLong() - startedAt;
        return !flashing.isEmpty() && elapsed < DURATION_MILLIS && (elapsed / PHASE_MILLIS) % 2 == 0;
    }

    void update() {
        repaintFlashing();
        if (clock.getAsLong() - startedAt >= DURATION_MILLIS) {
            timer.stop();
            flashing.clear();
        }
    }

    void paint(final Graphics2D graphics) {
        if (!isOn()) {
            return;
        }
        for (final MultiTileArea blocker : flashing) {
            drawBorder(graphics, stripes, pixelBox(blocker));
        }
    }

    void stop() {
        timer.stop();
        flashing.clear();
    }

    private void repaintFlashing() {
        flashing.forEach(blocker -> repainter.accept(pixelBox(blocker)));
    }

    private TexturePaint stripes() {
        final int size = 2 * STRIPE_WIDTH;
        final BufferedImage image = new BufferedImage(size, size, TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, ((x + y) / STRIPE_WIDTH) % 2 == 0 ? STRIPE_YELLOW : STRIPE_BLACK);
            }
        }
        return new TexturePaint(image, new Rectangle(0, 0, size, size));
    }
}
