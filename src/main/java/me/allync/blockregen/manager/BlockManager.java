package me.allync.blockregen.manager;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class BlockManager {

    private final BlockRegen plugin;
    // Changed the map key from Material to String to support ItemsAdder IDs
    private final Map<String, BlockData> blockDataMap = new HashMap<>();

    public BlockManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public void loadBlocks() {
        blockDataMap.clear();
        File blocksFile = new File(plugin.getDataFolder(), "blocks.yml");
        if (!blocksFile.exists()) {
            plugin.saveResource("blocks.yml", false);
        }

        FileConfiguration blocksConfig = YamlConfiguration.loadConfiguration(blocksFile);
        for (String key : blocksConfig.getKeys(false)) {
            // The key can now be a vanilla material (e.g., "DIAMOND_ORE")
            // or an ItemsAdder namespaced ID (e.g., "itemsadder:ruby_ore").
            // We no longer validate it as a Material here, allowing for custom strings.

            ConfigurationSection section = blocksConfig.getConfigurationSection(key);
            if (section != null) {
                try {
                    BlockData data = new BlockData(section);
                    // Store with a normalized key: lowercase for namespaced IDs, uppercase for vanilla materials.
                    // This ensures lookups always match regardless of how the identifier was returned.
                    String normalizedKey = key.contains(":") ? key.toLowerCase() : key.toUpperCase();
                    blockDataMap.put(normalizedKey, data);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load block data for " + key, e);
                }
            }
        }
        plugin.getLogger().info("Loaded " + blockDataMap.size() + " block configurations from blocks.yml.");
    }

    /**
     * Gets the BlockData for a given identifier.
     * @param identifier The block identifier (e.g., "DIAMOND_ORE" or "itemsadder:ruby_ore").
     * @return The BlockData, or null if not found.
     */
    public BlockData getBlockData(String identifier) {
        if (identifier == null) return null;
        // Normalize lookup key: lowercase for namespaced (contains ':'), uppercase for vanilla.
        String normalizedKey = identifier.contains(":") ? identifier.toLowerCase() : identifier.toUpperCase();
        return blockDataMap.get(normalizedKey);
    }

    public BlockData getBlockData(String identifier, String regionName) {
        BlockData data = getBlockData(identifier);
        if (data == null) {
            return null;
        }
        return data.isRegionAllowed(regionName) ? data : null;
    }

    public BlockData getBlockData(String identifier, Collection<String> regionNames) {
        BlockData data = getBlockData(identifier);
        if (data == null) {
            return null;
        }
        return data.isRegionAllowed(regionNames) ? data : null;
    }

    /**
     * Checks if a block identifier is a configured regen block.
     * @param identifier The block identifier (e.g., "DIAMOND_ORE" or "itemsadder:ruby_ore").
     * @return True if it is a regen block, false otherwise.
     */
    public boolean isRegenBlock(String identifier) {
        if (identifier == null) return false;
        String normalizedKey = identifier.contains(":") ? identifier.toLowerCase() : identifier.toUpperCase();
        return blockDataMap.containsKey(normalizedKey);
    }

    public boolean isRegenBlockInRegion(String identifier, String regionName) {
        return getBlockData(identifier, regionName) != null;
    }

    public boolean isRegenBlockInRegion(String identifier, Collection<String> regionNames) {
        return getBlockData(identifier, regionNames) != null;
    }

    public Set<String> getConfiguredIdentifiers() {
        Set<String> identifiers = new HashSet<>();
        for (String key : blockDataMap.keySet()) {
            identifiers.add(key.contains(":") ? key.toLowerCase() : key);
        }
        return identifiers;
    }
}
