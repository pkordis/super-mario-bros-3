package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.game.Physics;

import java.util.Map;

public interface PhysicsRepository {

    Map<Physics.Parameter, Float> load();

    void save(Map<Physics.Parameter, Float> values);
}

