package me.allync.blockregen.data;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Immutable key for a block position.
 */
public final class MiningTargetKey {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    private MiningTargetKey(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static MiningTargetKey from(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and world must not be null");
        }
        return new MiningTargetKey(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MiningTargetKey other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z && worldName.equals(other.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z);
    }
}

