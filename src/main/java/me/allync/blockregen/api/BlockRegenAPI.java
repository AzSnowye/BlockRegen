package me.allync.blockregen.api;

import org.bukkit.Location;
import org.bukkit.block.Block;
import java.util.Set;

public interface BlockRegenAPI {

    /**
     * Get the API instance.
     */
    static BlockRegenAPI get() {
        return InstanceHolder.instance;
    }

    /**
     * Set the API instance (for internal use only).
     */
    static void setInstance(BlockRegenAPI instance) {
        InstanceHolder.instance = instance;
    }

    class InstanceHolder {
        private static BlockRegenAPI instance;
    }

    /**
     * Check if a block at the location is currently regenerating.
     * @param location the location to check
     * @return true if regenerating, false otherwise
     */
    boolean isRegenerating(Location location);

    /**
     * Force regenerate a block at the location immediately if it is regenerating.
     * @param location the location of the block
     */
    void forceRegen(Location location);

    /**
     * Get remaining regeneration time in milliseconds.
     * @param location the location of the block
     * @return remaining time in milliseconds, or -1 if not regenerating
     */
    long getRemainingRegenTime(Location location);

    /**
     * Check if a location is inside any defined BlockRegen region.
     * @param location the location to check
     * @return true if inside a region, false otherwise
     */
    boolean isInRegenRegion(Location location);

    /**
     * Get the names of BlockRegen regions at a location.
     * @param location the location
     * @return a set of region names
     */
    Set<String> getRegionsAt(Location location);

    /**
     * Get the block configuration identifier for a block.
     * @param block the block to check
     * @return the configuration identifier, or null if not a BlockRegen block
     */
    String getBlockConfigIdentifier(Block block);

    /**
     * Get the block configuration identifier for a block with region context.
     * @param block the block to check
     * @param regionNames the regions the block is in
     * @return the configuration identifier, or null if not a BlockRegen block
     */
    String getBlockConfigIdentifier(Block block, Set<String> regionNames);

    /**
     * Check if a block is configured in BlockRegen.
     * @param block the block to check
     * @return true if configured, false otherwise
     */
    boolean isConfiguredBlock(Block block);

    /**
     * Check if a block is configured in BlockRegen with region context.
     * @param block the block to check
     * @param regionNames the regions the block is in
     * @return true if configured, false otherwise
     */
    boolean isConfiguredBlock(Block block, Set<String> regionNames);
}
