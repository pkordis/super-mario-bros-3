package house.x1337.app.smb3.enumeration;

/**
 * Which side of its block a reward comes out of: above it when the block is struck from below or from the side, below
 * it when the player lands on it (a note block). A reward that appears above the block whatever the side (a coin, a
 * P-Switch) does not care.
 */
public enum RewardDispenseSide {
    ABOVE,
    BELOW
}