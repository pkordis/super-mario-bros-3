package house.x1337.app.smb3.ui.editor.level.tab;

import com.jme3.system.awt.AwtPanel;
import com.jme3.system.awt.AwtPanelsContext;
import com.jme3.system.awt.PaintMode;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.ui.editor.level.tab.core.BaseLevelSceneTab;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static java.awt.BorderLayout.CENTER;
import static java.awt.Color.BLACK;

@Prototype
@RequiredArgsConstructor
public class GameEngineTesterTab extends BaseLevelSceneTab {
    private final GameEngine gameEngine;

    @PostConstruct
    void init() {
        setLayout(new BorderLayout());
        setBackground(BLACK);

        final Canvas engineCanvas = createCanvasForTheGameEngine();
        if (engineCanvas != null) {
            add(engineCanvas, CENTER);
            // Clicking the wrapper focuses the engine canvas so keyboard input works
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent e) {
                    engineCanvas.requestFocusInWindow();
                }
            });
        }
    }

    private Canvas createCanvasForTheGameEngine() {
        if (!(gameEngine.getContext() instanceof AwtPanelsContext ctx)) {
            return null;
        }
        final AwtPanel panel = ctx.createPanel(PaintMode.Accelerated);
        ctx.setInputSource(panel);
        gameEngine.enqueue(() -> panel.attachTo(
            true,
            gameEngine.getViewPort(),
            gameEngine.getGuiViewPort())
        );
        return panel;
    }
}
