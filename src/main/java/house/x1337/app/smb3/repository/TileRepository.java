package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.TileRecord;

import java.util.Optional;

public interface TileRepository {

    void insert(TileRecord record);

    Iterable<TileRecord> findAll();

    Optional<TileRecord> findById(int id);

    void updateMetadata(int id, TileType type, String description, int[] argbData);
}
