package house.x1337.app.smb3.ui.editor.level.enemy.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.function.Supplier;

@Singleton
@RequiredArgsConstructor
public class EnemyContextMenu {
    private final EnemyWindows enemyWindows;

    /** The menu of a palette entry; {@code enemy} gives the entry's stamp as it is when the menu is used. */
    public JPopupMenu forEnemy(final Supplier<EnemyStamp> enemy) {
        final JMenuItem edit = new JMenuItem("Edit");
        edit.addActionListener(event -> enemyWindows.openEditor(enemy.get()));
        final JPopupMenu menu = new JPopupMenu();
        menu.add(edit);
        return menu;
    }
}
