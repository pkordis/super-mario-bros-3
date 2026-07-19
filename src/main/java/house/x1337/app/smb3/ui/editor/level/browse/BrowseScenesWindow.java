package house.x1337.app.smb3.ui.editor.level.browse;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.LevelSceneEditorWindow;
import lombok.Getter;

import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.ui.editor.level.browse.ButtonsBar.Button.DELETE;
import static house.x1337.app.smb3.ui.editor.level.browse.ButtonsBar.Button.LOAD_MORE;
import static house.x1337.app.smb3.ui.editor.level.browse.ButtonsBar.Button.OPEN;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.YES_NO_OPTION;
import static javax.swing.JOptionPane.YES_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;

@Prototype
public final class BrowseScenesWindow extends JDialog implements ComponentsBuilder {
    private static final int PAGE_SIZE = 25;

    private final JTable table = buildTable();
    private final LevelScenePreviewPanel previewPanel;
    private final ButtonsBar buttonsBar;

    @Getter
    private final List<LevelScene> loadedLevelScenes = new ArrayList<>();
    private final LevelSceneEditorTabSystem levelSceneEditorTabSystem;
    private final LevelSceneService levelSceneService;

    private int currentOffset = 0;

    public static void launch() {
        getBean(
            BrowseScenesWindow.class,
            getBean(LevelSceneEditorWindow.class)
        );
    }

    public BrowseScenesWindow(final LevelSceneEditorWindow owner) {
        super(owner, "Browse Scenes", true);

        levelSceneService = getBean(LevelSceneService.class);
        levelSceneEditorTabSystem = getBean(LevelSceneEditorTabSystem.class);
        previewPanel = getBean(LevelScenePreviewPanel.class);
        buttonsBar = getBean(ButtonsBar.class);
        buttonsBar.disable(DELETE, OPEN);
        buttonsBar.onActionEvent(OPEN, this::openSelected);
        buttonsBar.onActionEvent(DELETE, this::deleteSelected);
        buttonsBar.onActionEvent(LOAD_MORE, this::loadNextPage);

        setResizable(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContentPanel(table, previewPanel, buttonsBar));
        setMinimumSize(new Dimension(840, 500));
        setPreferredSize(new Dimension(980, 580));
        pack();
        setLocationRelativeTo(owner);

        loadNextPage();
        setVisible(true);
    }

    private void loadNextPage() {
        final List<LevelScene> page = levelSceneService.findPage(currentOffset, PAGE_SIZE);
        for (final LevelScene env : page) {
            loadedLevelScenes.add(env);
            getTableModel().addRow(buildRow(env));
        }
        currentOffset += page.size();

        // Disable "Load More" when the last fetch returned a partial page.
        buttonsBar.enable(page.size() == PAGE_SIZE, LOAD_MORE);
    }

    @Override
    public void onRowSelected(final int viewRow) {
        if (viewRow < 0 || viewRow >= loadedLevelScenes.size()) {
            previewPanel.renderPreview(null);
            buttonsBar.disable(DELETE, OPEN);
            return;
        }
        final LevelScene levelScene = loadedLevelScenes.get(viewRow);
        previewPanel.renderPreview(levelScene);
        buttonsBar.enable(DELETE, OPEN);
    }

    @Override
    public DefaultTableModel getTableModel() {
        return (DefaultTableModel) table.getModel();
    }

    private void openSelected() {
        final int viewRow = table.getSelectedRow();
        if (viewRow < 0 || viewRow >= loadedLevelScenes.size()) {
            return;
        }
        final LevelScene levelScene = loadedLevelScenes.get(viewRow);
        levelSceneEditorTabSystem.open(levelScene);
        dispose();
    }

    private void deleteSelected() {
        final int row = table.getSelectedRow();
        if (row < 0 || row >= loadedLevelScenes.size()) {
            return;
        }
        final LevelScene env = loadedLevelScenes.get(row);
        final String displayName = (env.getTitle() != null && !env.getTitle().isBlank())
            ? env.getTitle() : String.valueOf(env.getId());
        final int choice = showConfirmDialog(
            this,
            "Delete Scene \"" + displayName + "\"?\nThis action cannot be undone.",
            "Delete Scene",
            YES_NO_OPTION,
            WARNING_MESSAGE
        );
        if (choice != YES_OPTION) {
            return;
        }
        levelSceneService.delete(env.getId());
        refreshView();
    }

    private void refreshView() {
        loadedLevelScenes.clear();
        getTableModel().setRowCount(0);
        currentOffset = 0;
        previewPanel.renderPreview(null);
        buttonsBar.disable(DELETE, OPEN);
        loadNextPage();
    }

    @Override
    public void onFieldValueChangeRequest(
        final LevelScene levelScene,
        final Field field,
        final String newValue
    ) {
        switch (field) {
            case TITLE -> levelScene.setTitle(newValue);
            case DESCRIPTION -> levelScene.setDescription(newValue);
        }
        levelSceneService.save(levelScene);
    }
}
