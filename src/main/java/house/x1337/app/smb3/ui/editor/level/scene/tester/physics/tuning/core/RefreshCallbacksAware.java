package house.x1337.app.smb3.ui.editor.level.scene.tester.physics.tuning.core;

import java.util.List;

public interface RefreshCallbacksAware {
    List<Runnable> getRefreshCallbacks();

    default void refreshAll() {
        for (final Runnable r : getRefreshCallbacks()) {
            r.run();
        }
    }
}
