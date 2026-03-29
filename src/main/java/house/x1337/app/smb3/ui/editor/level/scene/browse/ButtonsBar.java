package house.x1337.app.smb3.ui.editor.level.scene.browse;

import house.x1337.app.smb3.annotation.Prototype;

import javax.swing.JButton;
import javax.swing.JPanel;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

import static java.awt.FlowLayout.RIGHT;

@Prototype
public class ButtonsBar extends JPanel {
    private final JButton openButton = new JButton("Open");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton loadMoreButton = new JButton("Load More");

    public ButtonsBar() {
        super(new FlowLayout(RIGHT, 6, 0));
        add(loadMoreButton);
        add(deleteButton);
        add(openButton);
    }

    public JButton getButton(final Button button) {
        return switch (button) {
            case DELETE -> deleteButton;
            case LOAD_MORE -> loadMoreButton;
            case OPEN -> openButton;
        };
    }

    public void enable(final Button... buttons) {
        enable(true, buttons);
    }

    public void enable(
        final boolean condition,
        final Button... buttons
    ) {
        for (final Button button : buttons) {
            getButton(button).setEnabled(condition);
        }
    }

    public void disable(final Button... buttons) {
        for (final Button button : buttons) {
            getButton(button).setEnabled(false);
        }
    }

    public void onActionEvent(final Button button, final Consumer<ActionEvent> actionEventConsumer) {
        getButton(button).addActionListener(actionEventConsumer::accept);
    }

    public void onActionEvent(final Button button, final Runnable runnable) {
        onActionEvent(button, e -> runnable.run());
    }

    public enum Button {
        DELETE,
        LOAD_MORE,
        OPEN
    }
}
