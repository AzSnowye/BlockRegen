package me.allync.blockregen.manager;

import dev.lone.itemsadder.api.CustomBlock;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.util.NexoUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RandomOreManager {

    private static final String LEGACY_REGION_ID = "global";
    private static final String MIGRATED_REGION_ID = "legacy_migrated";

    private final BlockRegen plugin;
    private final File randomOresFile;

    private FileConfiguration randomOresConfig;
    private final Map<String, RegionEntry> regionEntries = new HashMap<>();
    private final Map<String, Set<String>> pointToRegionsIndex = new HashMap<>();
    private final Map<String, Integer> relocationBlacklist = new HashMap<>();
    private final Map<String, RegionCycleDebug> regionDebugSnapshots = new HashMap<>();

    private boolean enabled;
    private long intervalTicks;
    private boolean strictRelocateEnabled;
    private int strictRelocateMinCycles;
    private int strictRelocateMaxCycles;

    public RandomOreManager(BlockRegen plugin) {
        this.plugin = plugin;
        this.randomOresFile = new File(plugin.getDataFolder(), "random-ores.yml");
        if (!this.randomOresFile.exists()) {
            plugin.saveResource("random-ores.yml", false);
        }
        this.randomOresConfig = YamlConfiguration.loadConfiguration(this.randomOresFile);
    }

    public void load() {
        this.randomOresConfig = YamlConfiguration.loadConfiguration(this.randomOresFile);
        this.regionEntries.clear();
        this.pointToRegionsIndex.clear();
        this.relocationBlacklist.clear();
        this.regionDebugSnapshots.clear();

        maybeAutoMigrateLegacyFormat();

        this.enabled = randomOresConfig.getBoolean("settings.enabled", false);
        long intervalSeconds = Math.max(1L, randomOresConfig.getLong("settings.interval-seconds", 30L));
        this.intervalTicks = intervalSeconds * 20L;
        loadStrictRelocateSettings();

        if (!loadRegionBasedConfig()) {
            loadLegacyConfig();
        }

        rebuildPointIndex();
    }

    private void loadStrictRelocateSettings() {
        this.strictRelocateEnabled = false;
        this.strictRelocateMinCycles = 1;
        this.strictRelocateMaxCycles = 2;

        Object node = randomOresConfig.get("settings.strict-relocate");
        if (node instanceof Boolean boolValue) {
            this.strictRelocateEnabled = boolValue;
            return;
        }

        ConfigurationSection section = randomOresConfig.getConfigurationSection("settings.strict-relocate");
        if (section != null) {
            this.strictRelocateEnabled = section.getBoolean("enabled", false);
            this.strictRelocateMinCycles = Math.max(1, section.getInt("min-cycles", 1));
            this.strictRelocateMaxCycles = Math.max(this.strictRelocateMinCycles, section.getInt("max-cycles", 2));
        }
    }

    private void maybeAutoMigrateLegacyFormat() {
        ConfigurationSection regionsSection = randomOresConfig.getConfigurationSection("regions");
        if (regionsSection != null && !regionsSection.getKeys(false).isEmpty()) {
            return;
        }

        ConfigurationSection legacyOres = randomOresConfig.getConfigurationSection("ores");
        if (legacyOres == null || legacyOres.getKeys(false).isEmpty()) {
            return;
        }

        int combinedMaxActive = 0;
        for (String blockId : legacyOres.getKeys(false)) {
            combinedMaxActive += Math.max(0, legacyOres.getInt(blockId + ".max-active", 1));

            ConfigurationSection oldBlockSection = legacyOres.getConfigurationSection(blockId);
            if (oldBlockSection == null) {
                continue;
            }

            String newPath = "regions." + MIGRATED_REGION_ID + ".blocks." + blockId;
            randomOresConfig.set(newPath + ".spawn-chance", oldBlockSection.getDouble("spawn-chance", 50.0D));
            randomOresConfig.set(newPath + ".empty-block", oldBlockSection.getString("empty-block", "STONE"));
            randomOresConfig.set(newPath + ".points", oldBlockSection.getStringList("points"));
        }

        randomOresConfig.set("regions." + MIGRATED_REGION_ID + ".max-active", Math.max(1, combinedMaxActive));
        randomOresConfig.set("ores", null);

        try {
            randomOresConfig.save(randomOresFile);
            plugin.getLogger().info("Migrasi otomatis random-ores.yml selesai: format lama 'ores' -> 'regions." + MIGRATED_REGION_ID + "'.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Gagal migrasi otomatis random-ores.yml: " + ex.getMessage());
        }
    }

    private boolean loadRegionBasedConfig() {
        ConfigurationSection regionsSection = randomOresConfig.getConfigurationSection("regions");
        if (regionsSection == null) {
            return false;
        }

        for (String regionName : regionsSection.getKeys(false)) {
            ConfigurationSection regionSection = regionsSection.getConfigurationSection(regionName);
            if (regionSection == null) {
                continue;
            }

            int maxActive = Math.max(0, regionSection.getInt("max-active", 1));
            RegionEntry regionEntry = new RegionEntry(regionName, maxActive);

            ConfigurationSection blocksSection = regionSection.getConfigurationSection("blocks");
            if (blocksSection == null) {
                continue;
            }

            for (String blockIdentifier : blocksSection.getKeys(false)) {
                ConfigurationSection blockSection = blocksSection.getConfigurationSection(blockIdentifier);
                if (blockSection == null) {
                    continue;
                }

                BlockEntry blockEntry = parseBlockEntry(blockIdentifier, blockSection);
                if (blockEntry != null) {
                    regionEntry.blocks.put(blockIdentifier.toUpperCase(Locale.ROOT), blockEntry);
                }
            }

            if (!regionEntry.blocks.isEmpty()) {
                regionEntries.put(regionEntry.regionNameLower, regionEntry);
            }
        }

        return !regionEntries.isEmpty();
    }

    private void loadLegacyConfig() {
        ConfigurationSection oresSection = randomOresConfig.getConfigurationSection("ores");
        if (oresSection == null) {
            return;
        }

        RegionEntry legacyRegion = new RegionEntry(LEGACY_REGION_ID, Integer.MAX_VALUE);
        for (String identifier : oresSection.getKeys(false)) {
            ConfigurationSection oreSection = oresSection.getConfigurationSection(identifier);
            if (oreSection == null) {
                continue;
            }
            BlockEntry blockEntry = parseBlockEntry(identifier, oreSection);
            if (blockEntry != null) {
                legacyRegion.blocks.put(identifier.toUpperCase(Locale.ROOT), blockEntry);
            }
        }

        if (!legacyRegion.blocks.isEmpty()) {
            regionEntries.put(legacyRegion.regionNameLower, legacyRegion);
        }
    }

    private void rebuildPointIndex() {
        pointToRegionsIndex.clear();
        for (RegionEntry regionEntry : regionEntries.values()) {
            for (BlockEntry blockEntry : regionEntry.blocks.values()) {
                for (Location point : blockEntry.points) {
                    String key = pointKey(point);
                    pointToRegionsIndex.computeIfAbsent(key, ignored -> new HashSet<>()).add(regionEntry.regionNameLower);
                }
            }
        }
    }

    private BlockEntry parseBlockEntry(String identifier, ConfigurationSection section) {
        List<Location> points = new ArrayList<>();
        for (String rawPoint : section.getStringList("points")) {
            Location parsed = parsePoint(rawPoint);
            if (parsed != null) {
                points.add(parsed);
            }
        }

        if (points.isEmpty()) {
            return null;
        }

        String emptyBlockRaw = section.getString("empty-block", "");
        Material emptyBlock = parseMaterial(emptyBlockRaw);
        if (emptyBlock == null) {
            BlockData blockData = plugin.getBlockManager().getBlockData(identifier);
            emptyBlock = blockData != null ? blockData.getReplacedBlock() : Material.STONE;
        }

        double spawnChance = clampChance(section.getDouble("spawn-chance", 50.0D));
        return new BlockEntry(identifier, spawnChance, emptyBlock, points);
    }

    public boolean isEnabled() {
        return enabled && !regionEntries.isEmpty();
    }

    public long getIntervalTicks() {
        return intervalTicks;
    }

    public Collection<String> getRegionNames() {
        return new ArrayList<>(regionEntries.keySet());
    }

    public Collection<String> getConfiguredBlockIdentifiers(String regionName) {
        RegionEntry regionEntry = regionEntries.get(regionName.toLowerCase(Locale.ROOT));
        if (regionEntry == null) {
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<>();
        for (BlockEntry blockEntry : regionEntry.blocks.values()) {
            ids.add(blockEntry.blockIdentifier);
        }
        return ids;
    }

    public boolean addPoint(String regionName, String blockIdentifier, Location location) {
        if (regionName == null || regionName.isEmpty() || blockIdentifier == null || blockIdentifier.isEmpty() || location == null || location.getWorld() == null) {
            return false;
        }

        String normalizedRegion = regionName.toLowerCase(Locale.ROOT);
        String path = "regions." + normalizedRegion + ".blocks." + blockIdentifier;
        List<String> points = new ArrayList<>(randomOresConfig.getStringList(path + ".points"));
        String serialized = serializePoint(location);
        if (points.contains(serialized)) {
            return false;
        }

        points.add(serialized);
        randomOresConfig.set(path + ".points", points);
        if (!randomOresConfig.isSet("regions." + normalizedRegion + ".max-active")) {
            randomOresConfig.set("regions." + normalizedRegion + ".max-active", 1);
        }
        if (!randomOresConfig.isSet(path + ".spawn-chance")) {
            randomOresConfig.set(path + ".spawn-chance", 50.0D);
        }
        if (!randomOresConfig.isSet(path + ".empty-block")) {
            BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier);
            randomOresConfig.set(path + ".empty-block", data != null ? data.getReplacedBlock().name() : Material.STONE.name());
        }

        return saveAndReload();
    }

    public boolean removePoint(String regionName, String blockIdentifier, Location location) {
        if (regionName == null || regionName.isEmpty() || blockIdentifier == null || blockIdentifier.isEmpty() || location == null || location.getWorld() == null) {
            return false;
        }

        String normalizedRegion = regionName.toLowerCase(Locale.ROOT);
        String path = "regions." + normalizedRegion + ".blocks." + blockIdentifier;
        List<String> points = new ArrayList<>(randomOresConfig.getStringList(path + ".points"));
        boolean removed = points.remove(serializePoint(location));
        if (!removed) {
            return false;
        }

        if (points.isEmpty()) {
            randomOresConfig.set(path, null);
        } else {
            randomOresConfig.set(path + ".points", points);
        }

        ConfigurationSection blocksSection = randomOresConfig.getConfigurationSection("regions." + normalizedRegion + ".blocks");
        if (blocksSection != null && blocksSection.getKeys(false).isEmpty()) {
            randomOresConfig.set("regions." + normalizedRegion, null);
        }

        return saveAndReload();
    }

    public int getPointCount(String regionName, String blockIdentifier) {
        RegionEntry regionEntry = regionEntries.get(regionName.toLowerCase(Locale.ROOT));
        if (regionEntry == null) {
            return 0;
        }

        BlockEntry blockEntry = regionEntry.blocks.get(blockIdentifier.toUpperCase(Locale.ROOT));
        return blockEntry == null ? 0 : blockEntry.points.size();
    }

    public int refreshRegionNow(String regionName) {
        advanceBlacklistCycle();
        if (regionName == null || regionName.isEmpty() || regionName.equalsIgnoreCase("all")) {
            randomizeNow();
            return regionEntries.size();
        }

        RegionEntry regionEntry = regionEntries.get(regionName.toLowerCase(Locale.ROOT));
        if (regionEntry == null) {
            return 0;
        }
        randomizeRegion(regionEntry, Collections.emptySet());
        return 1;
    }

    public int spawnBlockInRegion(String regionName, String blockIdentifier, int amount) {
        if (amount <= 0) {
            return 0;
        }

        RegionEntry regionEntry = regionEntries.get(regionName.toLowerCase(Locale.ROOT));
        if (regionEntry == null) {
            return 0;
        }

        BlockEntry blockEntry = regionEntry.blocks.get(blockIdentifier.toUpperCase(Locale.ROOT));
        if (blockEntry == null) {
            return 0;
        }

        List<Location> points = new ArrayList<>(blockEntry.points);
        Collections.shuffle(points);

        int placed = 0;
        for (Location point : points) {
            if (placed >= amount) {
                break;
            }
            if (plugin.getRegenManager().isRegenerating(point) || plugin.getMiningManager().isBeingMined(point)) {
                continue;
            }

            clearToEmpty(blockEntry.emptyBlock, point);
            if (placeIdentifier(blockEntry.blockIdentifier, point)) {
                placed++;
            }
        }

        return placed;
    }

    public void randomizeNow() {
        advanceBlacklistCycle();
        for (RegionEntry regionEntry : regionEntries.values()) {
            randomizeRegion(regionEntry, Collections.emptySet());
        }
    }

    public boolean isManagedPoint(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return pointToRegionsIndex.containsKey(pointKey(location));
    }

    public void onPointRegenerated(Location location) {
        if (!enabled || location == null || location.getWorld() == null) {
            return;
        }

        String regeneratedKey = pointKey(location);
        if (strictRelocateEnabled) {
            int cycles = ThreadLocalRandom.current().nextInt(strictRelocateMinCycles, strictRelocateMaxCycles + 1);
            relocationBlacklist.put(regeneratedKey, cycles);
        }

        Set<String> regions = pointToRegionsIndex.get(regeneratedKey);
        if (regions == null || regions.isEmpty()) {
            return;
        }

        for (String regionName : regions) {
            RegionEntry regionEntry = regionEntries.get(regionName);
            if (regionEntry != null) {
                randomizeRegion(regionEntry, Collections.singleton(regeneratedKey));
            }
        }
    }

    public List<String> getRegionDebugLines(String regionName) {
        RegionCycleDebug debug = regionDebugSnapshots.get(regionName.toLowerCase(Locale.ROOT));
        if (debug == null) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        lines.add("&eRegion: &f" + debug.regionName);
        lines.add("&eCycle: &f" + debug.cycleTimeMs);
        lines.add("&eTotal point: &f" + debug.totalPoints + " &7| &eCap: &f" + debug.cap);
        lines.add("&eSelected: &a" + debug.selected.size());
        lines.add("&eSkip blacklist: &c" + debug.skippedBlacklist + " &7| &eSkip mining: &c" + debug.skippedMining + " &7| &eSkip regen: &c" + debug.skippedRegen);
        for (String selected : debug.selected) {
            lines.add("&7- &f" + selected);
        }
        return lines;
    }

    private void advanceBlacklistCycle() {
        if (relocationBlacklist.isEmpty()) {
            return;
        }

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : relocationBlacklist.entrySet()) {
            int next = entry.getValue() - 1;
            if (next <= 0) {
                toRemove.add(entry.getKey());
            } else {
                entry.setValue(next);
            }
        }
        for (String key : toRemove) {
            relocationBlacklist.remove(key);
        }
    }

    private void randomizeRegion(RegionEntry regionEntry, Set<String> excludedKeys) {
        Map<String, PointMeta> allPoints = new HashMap<>();
        List<BlockEntry> weightedBlocks = new ArrayList<>();
        int skippedBlacklist = 0;
        int skippedMining = 0;
        int skippedRegen = 0;
        List<String> selectedPoints = new ArrayList<>();

        for (BlockEntry blockEntry : regionEntry.blocks.values()) {
            if (blockEntry.spawnChance > 0.0D) {
                weightedBlocks.add(blockEntry);
            }

            for (Location point : blockEntry.points) {
                String key = pointKey(point);
                allPoints.putIfAbsent(key, new PointMeta(point, blockEntry.emptyBlock));
            }
        }

        if (allPoints.isEmpty()) {
            return;
        }

        List<PointMeta> availablePoints = new ArrayList<>();
        int reservedActiveSlots = 0;
        for (PointMeta meta : allPoints.values()) {
            String key = pointKey(meta.location);
            if (excludedKeys.contains(key)) {
                clearToEmpty(meta.emptyBlock, meta.location);
                continue;
            }

            if (strictRelocateEnabled && relocationBlacklist.containsKey(key)) {
                skippedBlacklist++;
                continue;
            }

            if (plugin.getRegenManager().isRegenerating(meta.location)) {
                skippedRegen++;
                continue;
            }

            if (plugin.getMiningManager().isBeingMined(meta.location)) {
                if (isRegionBlockAtLocation(regionEntry, meta.location)) {
                    reservedActiveSlots++;
                }
                skippedMining++;
                continue;
            }
            clearToEmpty(meta.emptyBlock, meta.location);
            availablePoints.add(meta);
        }

        if (availablePoints.isEmpty()) {
            return;
        }

        if (weightedBlocks.isEmpty() || regionEntry.maxActive <= 0) {
            return;
        }

        Collections.shuffle(availablePoints);
        int freeSlots = Math.max(0, regionEntry.maxActive - reservedActiveSlots);
        int cap = Math.min(freeSlots, availablePoints.size());
        for (int i = 0; i < cap; i++) {
            BlockEntry chosen = selectWeightedBlock(weightedBlocks);
            if (chosen != null) {
                PointMeta pointMeta = availablePoints.get(i);
                if (placeIdentifier(chosen.blockIdentifier, pointMeta.location)) {
                    selectedPoints.add(chosen.blockIdentifier + " @ " + serializePoint(pointMeta.location));
                }
            }
        }

        regionDebugSnapshots.put(regionEntry.regionNameLower,
                new RegionCycleDebug(regionEntry.regionNameLower, System.currentTimeMillis(), allPoints.size(), cap,
                        skippedBlacklist, skippedMining, skippedRegen, selectedPoints));
    }

    private boolean isRegionBlockAtLocation(RegionEntry regionEntry, Location location) {
        String identifier = plugin.getMiningManager().getBlockIdentifier(location.getBlock());
        if (identifier == null) {
            return false;
        }
        return regionEntry.blocks.containsKey(identifier.toUpperCase(Locale.ROOT));
    }

    private BlockEntry selectWeightedBlock(List<BlockEntry> blocks) {
        double total = 0.0D;
        for (BlockEntry entry : blocks) {
            total += entry.spawnChance;
        }

        if (total <= 0.0D) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0D;
        for (BlockEntry entry : blocks) {
            cumulative += entry.spawnChance;
            if (roll < cumulative) {
                return entry;
            }
        }

        return blocks.get(blocks.size() - 1);
    }

    private boolean placeIdentifier(String blockIdentifier, Location location) {
        if (blockIdentifier.toLowerCase(Locale.ROOT).startsWith("nexo:")) {
            return BlockRegen.nexoEnabled && NexoUtil.placeNexoBlock(blockIdentifier, location);
        }

        if (blockIdentifier.contains(":")) {
            if (!BlockRegen.itemsAdderEnabled) {
                return false;
            }
            try {
                return CustomBlock.place(blockIdentifier, location) != null;
            } catch (Throwable ignored) {
                return false;
            }
        }

        Material material = parseMaterial(blockIdentifier);
        if (material == null) {
            return false;
        }
        location.getBlock().setType(material, false);
        return true;
    }

    private void clearToEmpty(Material material, Location location) {
        if (location == null || material == null) {
            return;
        }
        NexoUtil.removeNexoBlock(location);
        location.getBlock().setType(material, false);
    }

    private boolean saveAndReload() {
        try {
            randomOresConfig.save(randomOresFile);
            load();
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Gagal menyimpan random-ores.yml: " + ex.getMessage());
            return false;
        }
    }

    private Material parseMaterial(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private double clampChance(double chance) {
        return Math.max(0.0D, Math.min(100.0D, chance));
    }

    private String serializePoint(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private String pointKey(Location location) {
        return serializePoint(location);
    }

    private Location parsePoint(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        String[] parts = raw.split(",");
        if (parts.length != 4) {
            return null;
        }

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class RegionEntry {
        private final String regionNameLower;
        private final int maxActive;
        private final Map<String, BlockEntry> blocks = new HashMap<>();

        private RegionEntry(String regionName, int maxActive) {
            this.regionNameLower = regionName.toLowerCase(Locale.ROOT);
            this.maxActive = maxActive;
        }
    }

    private static final class BlockEntry {
        private final String blockIdentifier;
        private final double spawnChance;
        private final Material emptyBlock;
        private final List<Location> points;

        private BlockEntry(String blockIdentifier, double spawnChance, Material emptyBlock, List<Location> points) {
            this.blockIdentifier = blockIdentifier;
            this.spawnChance = spawnChance;
            this.emptyBlock = emptyBlock;
            this.points = points;
        }
    }

    private static final class PointMeta {
        private final Location location;
        private final Material emptyBlock;

        private PointMeta(Location location, Material emptyBlock) {
            this.location = location;
            this.emptyBlock = emptyBlock;
        }
    }

    private static final class RegionCycleDebug {
        private final String regionName;
        private final long cycleTimeMs;
        private final int totalPoints;
        private final int cap;
        private final int skippedBlacklist;
        private final int skippedMining;
        private final int skippedRegen;
        private final List<String> selected;

        private RegionCycleDebug(String regionName, long cycleTimeMs, int totalPoints, int cap,
                                 int skippedBlacklist, int skippedMining, int skippedRegen, List<String> selected) {
            this.regionName = regionName;
            this.cycleTimeMs = cycleTimeMs;
            this.totalPoints = totalPoints;
            this.cap = cap;
            this.skippedBlacklist = skippedBlacklist;
            this.skippedMining = skippedMining;
            this.skippedRegen = skippedRegen;
            this.selected = selected;
        }
    }
}

