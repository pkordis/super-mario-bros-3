package house.x1337.app.smb3.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dizitart.no2.repository.annotations.Entity;
import org.dizitart.no2.repository.annotations.Id;

import java.io.Serializable;

@Entity(
    value = "sprites"
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sprite implements Serializable {
    @Id
    private Long id;
}
