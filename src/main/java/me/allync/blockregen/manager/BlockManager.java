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

        File blocksFolder = new File(plugin.getDataFolder(), "blocks");
        File legacyFile   = new File(plugin.getDataFolder(), "blocks.yml");

        // ── Migrasi otomatis: jika folder belum ada tapi blocks.yml ada,
        //    pindahkan blocks.yml ke dalam folder sebagai example.yml ────────
        if (!blocksFolder.exists() && legacyFile.exists()) {
            blocksFolder.mkdirs();
            File dest = new File(blocksFolder, "example.yml");
            if (legacyFile.renameTo(dest)) {
                plugin.getLogger().info("Migrated blocks.yml → blocks/example.yml");
            }
        }

        // ── Jika folder belum ada (fresh install), buat + copy resource ────
        if (!blocksFolder.exists()) {
            blocksFolder.mkdirs();
            copyDefaultBlockFile("blocks/vanilla.yml", blocksFolder, "vanilla.yml");
            copyDefaultBlockFile("blocks/custom.yml",  blocksFolder, "custom.yml");
        }

        // ── Baca semua .yml di dalam folder blocks/ (non-recursive) ─────────
        File[] ymlFiles = blocksFolder.listFiles(f -> f.isFile() && f.getName().endsWith(".yml"));
        if (ymlFiles == null || ymlFiles.length == 0) {
            plugin.getLogger().warning("No .yml files found in blocks/ folder!");
            return;
        }

        int totalLoaded = 0;
        for (File ymlFile : ymlFiles) {
            int count = loadFromFile(ymlFile);
            totalLoaded += count;
            plugin.getLogger().info("  └─ " + ymlFile.getName() + " → " + count + " block(s)");
        }
        plugin.getLogger().info("Loaded " + totalLoaded + " block configuration(s) from " + ymlFiles.length + " file(s) in blocks/.");
    }

    /**
     * Memuat semua konfigurasi blok dari satu file .yml.
     *
     * @param file File YAML yang akan dibaca
     * @return Jumlah blok yang berhasil di-load dari file ini
     */
    private int loadFromFile(File file) {
        FileConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to parse " + file.getName(), e);
            return 0;
        }

        int count = 0;
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) continue;
            try {
                BlockData data = new BlockData(section);
                String normalizedKey = key.contains(":") ? key.toLowerCase() : key.toUpperCase();
                if (blockDataMap.containsKey(normalizedKey)) {
                    plugin.getLogger().warning("Duplicate block key '" + key + "' in " + file.getName() + " — overwriting previous entry.");
                }
                blockDataMap.put(normalizedKey, data);
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load block '" + key + "' in " + file.getName(), e);
            }
        }
        return count;
    }

    /** Meng-copy file resource bawaan ke dalam folder blocks/ saat fresh install. */
    private void copyDefaultBlockFile(String resourcePath, File targetFolder, String targetName) {
        File dest = new File(targetFolder, targetName);
        if (dest.exists()) return;
        try (java.io.InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                java.nio.file.Files.copy(in, dest.toPath());
                plugin.getLogger().info("Created blocks/" + targetName + " (default).");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not write blocks/" + targetName + ": " + e.getMessage());
        }
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
