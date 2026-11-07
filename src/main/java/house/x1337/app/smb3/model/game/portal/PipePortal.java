package house.x1337.app.smb3.model.game.portal;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@Prototype
@NoArgsConstructor
@AllArgsConstructor
public final class PipePortal implements PipePortalCapabilities {
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private PipePortalType type;
    private String description;
    private String levelSceneId;
    private String linksToId;
    private VirtualObjectType virtualObjectType;
    private int column;
    private int row;

    public record Cell(int row, int column) {
    }
}
