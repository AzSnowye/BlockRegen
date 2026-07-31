package me.allync.blockregen.manager;

import dev.lone.itemsadder.api.CustomBlock;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.util.NexoUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.logging.Level;

public class BlockManager {

    private final BlockRegen plugin;
    // Changed the map key from Material to String to support ItemsAdder IDs
    private final Map<String, BlockData> blockDataMap = new HashMap<>();
    // Reverse mapping: vanilla block names (STONE, IRON_ORE, etc) → List<BlockData>
    // Multiple BlockData entries may reference the same vanilla material (e.g. floor1 and floor3 both use IRON_ORE)
    // Store a list so we can perform region-aware disambiguation later.
    private final Map<String, java.util.List<BlockData>> regenBlockMap = new HashMap<>();

    public BlockManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public void loadBlocks() {
        blockDataMap.clear();
        regenBlockMap.clear();

        File blocksFolder = new File(plugin.getDataFolder(), "blocks");
        File legacyFile   = new File(plugin.getDataFolder(), "blocks/blocks.yml");

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
                String normalizedKey = normalizeIdentifier(key);
                if (blockDataMap.containsKey(normalizedKey)) {
                    plugin.getLogger().warning("Duplicate block key '" + key + "' in " + file.getName() + " — overwriting previous entry.");
                }
                blockDataMap.put(normalizedKey, data);
                
                // --- BUILD REVERSE MAPPING: vanilla block names from regen-blocks → BlockData ---
                // This allows pure vanilla blocks (without custom config ID) to still be matched
                if (data.hasRegenVariants()) {
                    for (BlockData.RegenVariant variant : data.getRegenVariants()) {
                        String variantId = variant.getBlockIdentifier();
                        if (variantId != null && !variantId.isEmpty()) {
                            String normalizedVariant = normalizeIdentifier(variantId);
                            // Only add if it's a vanilla material name (not a custom namespace)
                            if (!variantId.contains(":") && !variantId.toLowerCase().startsWith("nexo:")) {
                                try {
                                    // Verify it's a valid vanilla material
                                    Material.valueOf(variantId.toUpperCase(Locale.ROOT));
                                    // Store mapping: vanilla name → list of BlockData that reference it
                                    // Keep insertion order per-file so behavior is deterministic but allow multiple entries
                                    java.util.List<BlockData> list = regenBlockMap.get(normalizedVariant);
                                    if (list == null) {
                                        list = new java.util.ArrayList<>();
                                        regenBlockMap.put(normalizedVariant, list);
                                    }
                                    // Avoid exact duplicates
                                    if (!list.contains(data)) {
                                        list.add(data);
                                    }
                                } catch (IllegalArgumentException ignored) {
                                    // Not a vanilla material, skip
                                }
                            }
                        }
                    }
                }
                // --- END BUILD REVERSE MAPPING ---
                
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
        String normalizedKey = normalizeIdentifier(identifier);
        return blockDataMap.get(normalizedKey);
    }

    public BlockData getBlockData(Block block) {
        if (block == null) {
            return null;
        }

        java.util.List<BlockData> candidates = new java.util.ArrayList<>();
        for (BlockData data : blockDataMap.values()) {
            if (matchesConfiguredBlock(data, block)) {
                candidates.add(data);
            }
        }

        // If multiple configured BlockData matched the block type, try to pick the best candidate
        if (candidates.size() == 1) {
            return candidates.get(0);
        } else if (candidates.size() > 1) {
            // No region context available here — prefer non-region-specific BlockData, then smallest region set, then deterministic fallback
            candidates.sort((a, b) -> {
                // Prefer higher priority
                if (a.getPriority() != b.getPriority()) {
                    return Integer.compare(b.getPriority(), a.getPriority());
                }
                // Prefer entries without region restriction
                if (a.hasRegionRestriction() && !b.hasRegionRestriction()) return 1;
                if (!a.hasRegionRestriction() && b.hasRegionRestriction()) return -1;
                // Prefer smaller allowedRegions (more specific)
                int cmp = Integer.compare(a.getAllowedRegions().size(), b.getAllowedRegions().size());
                if (cmp != 0) return cmp;
                // Deterministic fallback: configured id lexicographic
                return a.getConfiguredId().compareToIgnoreCase(b.getConfiguredId());
            });
            return candidates.get(0);
        }

        // Fallback: try to match against vanilla block names in regenBlockMap
        // This handles cases where a pure vanilla block (not specifically configured)
        // is broken but appears in some config's regen-blocks section
        String materialName = normalizeIdentifier(block.getType().name());
        java.util.List<BlockData> list = regenBlockMap.get(materialName);
        if (list == null || list.isEmpty()) return null;

        // Choose best candidate from list without region context
        list.sort((a, b) -> {
            // Prefer higher priority
            if (a.getPriority() != b.getPriority()) {
                return Integer.compare(b.getPriority(), a.getPriority());
            }
            if (a.hasRegionRestriction() && !b.hasRegionRestriction()) return 1;
            if (!a.hasRegionRestriction() && b.hasRegionRestriction()) return -1;
            int cmp = Integer.compare(a.getAllowedRegions().size(), b.getAllowedRegions().size());
            if (cmp != 0) return cmp;
            return a.getConfiguredId().compareToIgnoreCase(b.getConfiguredId());
        });
        return list.get(0);
    }

    /**
     * Gets the configured identifier for a block with region context.
     * This is needed to pick the correct BlockData when multiple floors have the same block type.
     * @param block The block
     * @param regionNames The regions the block is in (for context-aware matching)
     * @return The configured identifier or vanilla block name
     */
    public String getConfiguredIdentifier(Block block, Collection<String> regionNames) {
        if (block == null) {
            return null;
        }
        // Gather candidates that match the block type
        java.util.List<BlockData> candidates = new java.util.ArrayList<>();
        for (BlockData data : blockDataMap.values()) {
            if (matchesConfiguredBlock(data, block)) {
                candidates.add(data);
            }
        }

        // If direct configured candidates exist, choose the best by region specificity
        BlockData best = chooseBestCandidate(candidates, regionNames);
        if (best != null) return best.getConfiguredId();

        // Second try: fallback to vanilla block lookup, but there may be multiple BlockData referencing this vanilla material
        String materialName = normalizeIdentifier(block.getType().name());
        java.util.List<BlockData> list = regenBlockMap.get(materialName);
        if (list != null && !list.isEmpty()) {
            best = chooseBestCandidate(list, regionNames);
            if (best != null) return best.getConfiguredId();
        }

        // Third try: Nexo blocks
        String nexoId = BlockRegen.nexoEnabled ? NexoUtil.getNexoBlockId(block) : null;
        if (nexoId != null) {
            return nexoId;
        }

        // Fourth try: ItemsAdder blocks
        if (BlockRegen.itemsAdderEnabled) {
            try {
                CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
                if (customBlock != null && customBlock.getNamespacedID() != null && !customBlock.getNamespacedID().isEmpty()) {
                    return customBlock.getNamespacedID();
                }
            } catch (Throwable ignored) {
            }
        }

        // Final fallback: vanilla material name
        return block.getType().name();
    }

    public String getConfiguredIdentifier(Block block) {
        BlockData data = getBlockData(block);
        if (data != null) {
            return data.getConfiguredId();
        }

        if (block == null) {
            return null;
        }

        String nexoId = BlockRegen.nexoEnabled ? NexoUtil.getNexoBlockId(block) : null;
        if (nexoId != null) {
            return nexoId;
        }

        if (BlockRegen.itemsAdderEnabled) {
            try {
                CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
                if (customBlock != null && customBlock.getNamespacedID() != null && !customBlock.getNamespacedID().isEmpty()) {
                    return customBlock.getNamespacedID();
                }
            } catch (Throwable ignored) {
            }
        }

        return block.getType().name();
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
        String normalizedKey = normalizeIdentifier(identifier);
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
        for (BlockData data : blockDataMap.values()) {
            if (data != null && data.getConfiguredId() != null && !data.getConfiguredId().isEmpty()) {
                identifiers.add(data.getConfiguredId());
            }
        }
        return identifiers;
    }

    /**
     * Choose the best BlockData candidate from a list given the region context.
     * Scoring rules:
     *  - Prefer candidates with the largest intersection count with provided regionNames
     *  - If intersection counts are equal and > 0, prefer the candidate with smaller allowedRegions (more specific)
     *  - If no candidate matches any region (intersection == 0), prefer non-region-restricted entries
     *  - Deterministic fallback: configuredId lexicographic
     */
    private BlockData chooseBestCandidate(java.util.List<BlockData> candidates, Collection<String> regionNames) {
        if (candidates == null || candidates.isEmpty()) return null;

        // Normalize regionNames to lower-case set for comparisons
        java.util.Set<String> normalized = new java.util.HashSet<>();
        if (regionNames != null) {
            for (String r : regionNames) {
                if (r != null) normalized.add(r.toLowerCase(Locale.ROOT));
            }
        }

        // If there is only one, return it (fast path)
        if (candidates.size() == 1) return candidates.get(0);

        candidates.sort((a, b) -> {
            int aMatch = 0;
            int bMatch = 0;
            if (!normalized.isEmpty()) {
                for (String ar : a.getAllowedRegions()) if (ar != null && normalized.contains(ar.toLowerCase(Locale.ROOT))) aMatch++;
                for (String br : b.getAllowedRegions()) if (br != null && normalized.contains(br.toLowerCase(Locale.ROOT))) bMatch++;
            }
            // Primary: higher match count (region match is most important)
            if (aMatch != bMatch) return Integer.compare(bMatch, aMatch);

            // Secondary: compare priority
            if (a.getPriority() != b.getPriority()) {
                return Integer.compare(b.getPriority(), a.getPriority()); // higher priority first
            }


            // If none matched any region, prefer non-restricted entries
            if (aMatch == 0 && bMatch == 0) {
                if (a.hasRegionRestriction() && !b.hasRegionRestriction()) return 1;
                if (!a.hasRegionRestriction() && b.hasRegionRestriction()) return -1;
            }

            // Secondary: smaller allowedRegions (more specific)
            int cmp = Integer.compare(a.getAllowedRegions().size(), b.getAllowedRegions().size());
            if (cmp != 0) return cmp;

            // Final deterministic fallback
            return a.getConfiguredId().compareToIgnoreCase(b.getConfiguredId());
        });

        return candidates.get(0);
    }

    private boolean matchesConfiguredBlock(BlockData data, Block block) {
        if (data == null || block == null) {
            return false;
        }

        String blockId = data.getBlockId();
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }

        String lower = blockId.toLowerCase(Locale.ROOT);
        if (lower.startsWith("nexo:")) {
            String worldId = BlockRegen.nexoEnabled ? NexoUtil.getNexoBlockId(block) : null;
            return worldId != null && worldId.equalsIgnoreCase(blockId);
        }

        if (blockId.contains(":")) {
            try {
                CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
                return customBlock != null && blockId.equalsIgnoreCase(customBlock.getNamespacedID());
            } catch (Throwable ignored) {
                return false;
            }
        }

        try {
            Material material = Material.valueOf(blockId.toUpperCase(Locale.ROOT));
            return block.getType() == material;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String normalizeIdentifier(String identifier) {
        return identifier == null ? null : identifier.toLowerCase(Locale.ROOT);
    }
}
