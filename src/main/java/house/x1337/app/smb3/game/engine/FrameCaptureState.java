package house.x1337.app.smb3.game.engine;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.util.BufferUtils;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures every rendered frame to {@code <project-root>/captures/} as PNG files
 * named by wall-clock nanosecond timestamp.
 *
 * <p>Toggled on/off via {@link #toggleCapture()}, bound to {@code KEY_9} in
 * {@link GameEngine}.
 *
 * <h3>Why a post viewport?</h3>
 * JME3's {@code renderViewPort} calls {@code SceneProcessor.postFrame} <em>before</em>
 * it flushes the {@link com.jme3.renderer.queue.RenderQueue.Bucket#Translucent Translucent}
 * bucket. The player sprite lives in that bucket, so any processor attached to a main
 * viewport would always capture the frame without the player.
 *
 * <p>The solution is a dedicated empty {@code postView} viewport with no scene.
 * The {@code RenderManager} renders all main viewports (game scene + HUD, each
 * including their full translucent queues) before it starts the post viewports.
 * The capture processor reads the framebuffer in {@code preFrame} of this post
 * viewport — at that point the default framebuffer already contains the complete,
 * fully composited frame: game tiles, player sprite, and HUD strip.
 *
 * <p>PNG encoding is offloaded to a single-threaded daemon {@link ExecutorService}
 * so the render thread is never blocked by disk I/O.
 */
@Slf4j
public final class FrameCaptureState extends AbstractAppState {

    private static final String CAPTURES_DIR = "captures";

    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private RenderManager renderManager;
    private int windowWidth;
    private int windowHeight;
    private Path capturesPath;
    private ExecutorService ioExecutor;

    /** Dedicated empty post viewport whose processor fires after all main viewports finish. */
    private ViewPort postViewPort;
    private FrameCapture processor;

    // -------------------------------------------------------------------------
    // AppState lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize(final AppStateManager stateManager, final Application app) {
        super.initialize(stateManager, app);
        renderManager = app.getRenderManager();
        windowWidth = app.getCamera().getWidth();
        windowHeight = app.getCamera().getHeight();
        capturesPath = Paths.get(CAPTURES_DIR);
        ioExecutor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "frame-capture-io");
            t.setDaemon(true);
            return t;
        });

        // Create a post viewport with no scene. It exists solely so we have a
        // SceneProcessor hook that fires after every main viewport (game + HUD)
        // has fully rendered — including their Translucent buckets.
        final Camera dummyCam = app.getCamera().clone();
        postViewPort = renderManager.createPostView("FrameCapture-PostView", dummyCam);
        postViewPort.setClearFlags(false, false, false);
    }

    @Override
    public void cleanup() {
        stopCapture();
        renderManager.removePostView(postViewPort);
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
        super.cleanup();
    }

    // -------------------------------------------------------------------------
    // Toggle
    // -------------------------------------------------------------------------

    /**
     * Toggles frame capture on or off. First call starts capturing; the next call stops.
     */
    public void toggleCapture() {
        if (capturing.get()) {
            stopCapture();
        } else {
            startCapture();
        }
    }

    private void startCapture() {
        try {
            Files.createDirectories(capturesPath);
        } catch (final IOException e) {
            log.error("Failed to create captures directory: {}", capturesPath.toAbsolutePath(), e);
            return;
        }
        processor = new FrameCapture();
        postViewPort.addProcessor(processor);
        capturing.set(true);
        log.info("Frame capture STARTED — writing to {}", capturesPath.toAbsolutePath());
    }

    private void stopCapture() {
        if (processor != null) {
            postViewPort.removeProcessor(processor);
            processor = null;
        }
        capturing.set(false);
        log.info("Frame capture STOPPED");
    }

    // -------------------------------------------------------------------------
    // SceneProcessor — fires in the post viewport, after all main viewports
    // -------------------------------------------------------------------------

    private final class FrameCapture implements SceneProcessor {

        private ByteBuffer cpuBuffer;
        private boolean initialized = false;

        @Override
        public void initialize(final RenderManager rm, final ViewPort vp) {
            cpuBuffer = BufferUtils.createByteBuffer(windowWidth * windowHeight * 4);
            initialized = true;
        }

        @Override
        public void reshape(final ViewPort vp, final int w, final int h) {
            // Window resizes are not supported at runtime; buffer stays fixed.
        }

        @Override
        public boolean isInitialized() {
            return initialized;
        }

        /**
         * Fires at the start of the post viewport render pass — after all main viewports
         * (game scene + HUD) have finished, including their Translucent bucket flushes.
         * The default framebuffer now contains the complete frame: tiles, player, HUD.
         */
        @Override
        public void preFrame(final float tpf) {
            cpuBuffer.clear();
            // out is null here (pre-render, no framebuffer bound yet for this viewport),
            // but the default framebuffer already holds the full previous frame from
            // the main viewports. readFrameBuffer(null, ...) reads GL FBO 0 — which in
            // LWJGL3 is the actual window surface at this point in the pipeline.
            renderManager.getRenderer().readFrameBuffer(null, cpuBuffer);

            final long timestamp = System.nanoTime();
            final int captureWidth = windowWidth;
            final int captureHeight = windowHeight;

            final byte[] pixels = new byte[cpuBuffer.limit()];
            cpuBuffer.rewind();
            cpuBuffer.get(pixels);

            ioExecutor.submit(() -> writePng(pixels, captureWidth, captureHeight, timestamp));
        }

        @Override
        public void postQueue(final RenderQueue rq) {
            // nothing to do
        }

        @Override
        public void postFrame(final FrameBuffer out) {
            // nothing to do
        }

        @Override
        public void cleanup() {
            // nothing to release
        }

        @Override
        public void setProfiler(final AppProfiler profiler) {
            // profiling not required
        }
    }

    // -------------------------------------------------------------------------
    // PNG encoding (runs on the IO executor thread)
    // -------------------------------------------------------------------------

    private void writePng(
        final byte[] pixels,
        final int width,
        final int height,
        final long timestamp
    ) {
        // readFrameBuffer returns RGBA bytes, bottom-to-top row order.
        // BufferedImage is top-to-bottom, so rows are flipped while converting.
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int pixelIndex = 0;
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                final int r = pixels[pixelIndex] & 0xFF;
                final int g = pixels[pixelIndex + 1] & 0xFF;
                final int b = pixels[pixelIndex + 2] & 0xFF;
                // byte 3 is alpha — not needed for TYPE_INT_RGB
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
                pixelIndex += 4;
            }
        }

        final Path output = capturesPath.resolve(timestamp + ".png");
        try {
            ImageIO.write(image, "PNG", output.toFile());
        } catch (final IOException e) {
            log.error("Failed to write capture frame {}", output, e);
        }
    }
}
