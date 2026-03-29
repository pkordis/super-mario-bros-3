package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.TileRecord;

public interface TileRepository {

    void insert(TileRecord record);

    Iterable<TileRecord> findAll();

    void updateMetadata(int id, TileType type, String description, int[] argbData);
}

