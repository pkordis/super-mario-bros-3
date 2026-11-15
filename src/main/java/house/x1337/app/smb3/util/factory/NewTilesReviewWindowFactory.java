package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;
import house.x1337.app.smb3.ui.editor.level.tile.review.NewTilesReviewWindow;

import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface NewTilesReviewWindowFactory {
    static NewTilesReviewWindow fromTiles(final List<Tile> needReview) {
        return getBean(
            NewTilesReviewWindow.class,
            getBean(GameEditorWindow.class),
            needReview
        );
    }
}
