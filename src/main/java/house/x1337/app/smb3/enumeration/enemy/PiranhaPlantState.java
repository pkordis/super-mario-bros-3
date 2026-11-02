package house.x1337.app.smb3.enumeration.enemy;

public enum PiranhaPlantState {
    HIDDEN,
    EMERGING,
    OUT,
    RETRACTING;

    public PiranhaPlantState next() {
        final PiranhaPlantState[] states = values();
        return states[(ordinal() + 1) % states.length];
    }
}
