package house.x1337.app.smb3.ui.editor.level.enemy.edit;

import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EnemyContextMenuTest {
    private final EnemyWindows enemyWindows = mock(EnemyWindows.class);
    private final EnemyContextMenu enemyContextMenu = new EnemyContextMenu(enemyWindows);

    @Test
    @DisplayName("A palette enemy's menu holds Edit only, which opens the edit window for the entry's current stamp")
    void editOpensTheEditWindow() {
        // Prepare - the entry shows a newer copy of the stamp by the time the menu is used
        final AtomicReference<EnemyStamp> shown = new AtomicReference<>(EnemyStamp.builder().id("goomba").build());
        final JPopupMenu menu = enemyContextMenu.forEnemy(shown::get);
        final EnemyStamp edited = EnemyStamp.builder().id("goomba").build();
        shown.set(edited);

        // Execute
        final JMenuItem edit = (JMenuItem) menu.getComponent(0);
        edit.doClick();

        // Verify
        assertThat(menu.getComponentCount()).isEqualTo(1);
        assertThat(edit.getText()).isEqualTo("Edit");
        verify(enemyWindows).openEditor(edited);
    }
}
