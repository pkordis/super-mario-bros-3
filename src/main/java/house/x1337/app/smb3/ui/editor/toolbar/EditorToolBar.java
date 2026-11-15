package house.x1337.app.smb3.ui.editor.toolbar;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JToolBar;
import java.util.List;

import static java.util.Comparator.comparingInt;

@Singleton
@RequiredArgsConstructor
public class EditorToolBar extends JToolBar {
    private final List<EditorToolBarSection> sections;

    @PostConstruct
    void init() {
        setFloatable(false);
        setRollover(true);
        final List<EditorToolBarSection> ordered = sections.stream()
            .sorted(comparingInt(EditorToolBarSection::getOrder))
            .toList();
        for (int index = 0; index < ordered.size(); index++) {
            if (index > 0) {
                addSeparator();
            }
            ordered.get(index).getTools().forEach(this::add);
        }
    }
}
