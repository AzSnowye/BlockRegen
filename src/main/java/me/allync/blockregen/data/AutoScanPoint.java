package me.allync.blockregen.data;

import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Represents a single block location that is managed by the Auto-Scan system.
 * The block at this location randomly cycles between its "ore" state (active/mineable)
 * and a "placeholder" state (inactive/non-mineable).
 */
public class AutoScanPoint {

    private final String blockIdentifier; // e.g. "COAL_ORE", "nexo:agate_ore"
    private final Location location;
    private final Material placeholder;   // what it looks like when inactive (e.g. STONE)
    private boolean active;               // true = ore visible, false = placeholder visible

    public AutoScanPoint(String blockIdentifier, Location location, Material placeholder, boolean active) {
        this.blockIdentifier = blockIdentifier;
        this.location = location.clone();
        this.placeholder = placeholder;
        this.active = active;
    }

    public String getBlockIdentifier() {
        return blockIdentifier;
    }

    public Location getLocation() {
        return location.clone();
    }

    public Material getPlaceholder() {
        return placeholder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns a unique string key for this point based on its world+coordinates.
     */
    public String getKey() {
        return location.getWorld().getName()
                + ":" + location.getBlockX()
                + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }
}
