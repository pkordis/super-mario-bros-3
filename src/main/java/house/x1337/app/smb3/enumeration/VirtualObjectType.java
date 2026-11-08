package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.Color;

import static house.x1337.app.smb3.enumeration.VirtualObjectCategory.CARD;
import static house.x1337.app.smb3.enumeration.VirtualObjectCategory.PIPE_PORTAL;
import static java.awt.Color.RED;
import static java.awt.Color.YELLOW;

@Getter
@RequiredArgsConstructor
public enum VirtualObjectType {
    LEVEL_COMPLETION_CARD(CARD, YELLOW, 1, 1, "Level Completion Card"),
    PIPE_PORTAL_HORIZONTAL(PIPE_PORTAL, RED, 1, 2, "Pipe Portal (Horizontal)"),
    PIPE_PORTAL_VERTICAL(PIPE_PORTAL, RED, 2, 1, "Pipe Portal (Vertical)");

    private final VirtualObjectCategory category;
    private final Color colour;
    private final int rows;
    private final int columns;
    private final String label;
}
