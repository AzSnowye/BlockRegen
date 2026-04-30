package me.allync.blockregen.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.AutoScanPoint;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.util.ModelEngineUtil;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages the Auto-Scan point system.
 *
 * <p>Auto-Scan allows admins to designate specific block locations that are
 * automatically managed by the plugin. On each cycle, each registered location
 * has a configurable chance to show the real ore block (active/mineable) or a
 * placeholder block (inactive/non-mineable). This creates a dynamic "anti-AFK"
 * mining experience without needing to define region pools.</p>
 *
 * <p>Points are registered via a toggle wand (/br scan wand) or automatically
 * when an admin places a configured regen block while in scan-register mode.</p>
 */
public class AutoScanManager {

    private final BlockRegen plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // Keyed by location string for fast lookup
    private final Map<String, AutoScanPoint> points = new ConcurrentHashMap<>();

    // Config values (loaded from config.yml auto-scan section)
    private boolean enabled;
    private int cycleIntervalSeconds;
    private double defaultActiveChance;   // 0-100, default probability ore is visible each cycle
    private Material defaultPlaceholder;

    public AutoScanManager(BlockRegen plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "auto-scan-points.yml");
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    public void load() {
        // Load settings from config.yml
        FileConfiguration cfg = plugin.getConfig();
        this.enabled              = cfg.getBoolean("auto-scan.enabled", false);
        this.cycleIntervalSeconds = cfg.getInt("auto-scan.cycle-interval-seconds", 30);
        this.defaultActiveChance  = cfg.getDouble("auto-scan.default-active-chance", 50.0);

        String placeholderStr = cfg.getString("auto-scan.default-placeholder", "STONE");
        try {
            this.defaultPlaceholder = Material.valueOf(placeholderStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[AutoScan] Invalid auto-scan.default-placeholder '" + placeholderStr + "'. Defaulting to STONE.");
            this.defaultPlaceholder = Material.STONE;
        }

        // Load persisted points
        points.clear();
        if (!dataFile.exists()) {
            return;
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = dataConfig.getConfigurationSection("points");
        if (section == null) return;

        int loaded = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            String worldName     = entry.getString("world");
            int x                = entry.getInt("x");
            int y                = entry.getInt("y");
            int z                = entry.getInt("z");
            String blockId       = entry.getString("block-id");
            String placeholderS  = entry.getString("placeholder", "STONE");
            boolean active       = entry.getBoolean("active", false);

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[AutoScan] Skipping point in unknown world '" + worldName + "'.");
                continue;
            }

            Material ph;
            try {
                ph = Material.valueOf(placeholderS.toUpperCase());
            } catch (IllegalArgumentException ex) {
                ph = defaultPlaceholder;
            }

            Location loc = new Location(world, x, y, z);
            AutoScanPoint point = new AutoScanPoint(blockId, loc, ph, active);
            points.put(point.getKey(), point);
            loaded++;
        }

        plugin.getLogger().info("[AutoScan] Loaded " + loaded + " auto-scan points.");
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }

        // Wipe the points section and rewrite
        dataConfig.set("points", null);

        int index = 0;
        for (AutoScanPoint point : points.values()) {
            String path = "points." + index;
            dataConfig.set(path + ".world",       point.getLocation().getWorld().getName());
            dataConfig.set(path + ".x",           point.getLocation().getBlockX());
            dataConfig.set(path + ".y",           point.getLocation().getBlockY());
            dataConfig.set(path + ".z",           point.getLocation().getBlockZ());
            dataConfig.set(path + ".block-id",    point.getBlockIdentifier());
            dataConfig.set(path + ".placeholder",  point.getPlaceholder().name());
            dataConfig.set(path + ".active",       point.isActive());
            index++;
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[AutoScan] Could not save auto-scan-points.yml!", e);
        }
    }

    // -------------------------------------------------------------------------
    // Registration / Toggle
    // -------------------------------------------------------------------------

    /**
     * Toggles a location as an auto-scan point. If the block at the location is
     * a configured regen block, it is registered (or unregistered if already present).
     *
     * @return 1 if added, -1 if removed, 0 if the block is not a regen block.
     */
    public int toggle(Location location) {
        if (location == null || location.getWorld() == null) return 0;

        String key = buildKey(location);

        if (points.containsKey(key)) {
            AutoScanPoint point = points.remove(key);
            // Hapus model jika ada sebelum restore
            if (BlockRegen.modelEngineEnabled) {
                ModelEngineUtil.removeModel(location);
                ModelEngineUtil.restoreHiddenBlock(location);
            }
            restoreOreBlock(point);
            save();
            return -1; // removed
        }

        // Detect what regen block is at this location
        String blockId = plugin.getMiningManager().getBlockIdentifier(location.getBlock());
        if (!plugin.getBlockManager().isRegenBlock(blockId)) {
            return 0; // not a regen block
        }

        BlockData data = plugin.getBlockManager().getBlockData(blockId);
        Material placeholder = (data != null) ? data.getReplacedBlock() : defaultPlaceholder;

        // Start as active (ore visible) so the admin can see it's registered
        AutoScanPoint point = new AutoScanPoint(blockId, location, placeholder, true);
        points.put(key, point);
        save();
        return 1; // added
    }

    // -------------------------------------------------------------------------
    // Cycle Logic
    // -------------------------------------------------------------------------

    /**
     * Runs one full cycle: each point independently rolls its active-chance.
     */
    public void runCycle() {
        if (!enabled || points.isEmpty()) return;

        for (AutoScanPoint point : points.values()) {
            Location loc = point.getLocation();
            if (loc.getWorld() == null) continue;

            // NEW: Skip if block is currently being mined/touched by a player
            if (plugin.getBlockMiningListener() != null && plugin.getBlockMiningListener().isBlockBusy(loc)) {
                continue;
            }

            // Get per-block active chance if configured, otherwise use global default
            double chance = getActiveChance(point.getBlockIdentifier());
            boolean shouldBeActive = ThreadLocalRandom.current().nextDouble(100.0) < chance;

            if (shouldBeActive == point.isActive()) continue; // no change needed

            point.setActive(shouldBeActive);

            if (shouldBeActive) {
                // Place the ore block
                placeOreBlock(point);
                // Spawn model jika block ini punya model engine
                if (BlockRegen.modelEngineEnabled) {
                    spawnModelForBlock(point.getBlockIdentifier(), loc);
                }
            } else {
                // Hapus model dulu sebelum ganti jadi placeholder
                if (BlockRegen.modelEngineEnabled) {
                    ModelEngineUtil.removeModel(loc);
                    ModelEngineUtil.restoreHiddenBlock(loc);
                }
                // Batalkan mining task yang mungkin sedang aktif
                plugin.getMiningManager().cancelMiningAt(loc);
                loc.getBlock().setType(point.getPlaceholder(), false);
            }
        }

        save();
    }

    /**
     * Forces a specific point to cycle immediately (debug/admin use).
     */
    public boolean forceCyclePoint(Location location) {
        String key = buildKey(location);
        AutoScanPoint point = points.get(key);
        if (point == null) return false;

        boolean nowActive = !point.isActive();
        point.setActive(nowActive);
        if (nowActive) {
            placeOreBlock(point);
            if (BlockRegen.modelEngineEnabled) {
                spawnModelForBlock(point.getBlockIdentifier(), location);
            }
        } else {
            if (BlockRegen.modelEngineEnabled) {
                ModelEngineUtil.removeModel(location);
                ModelEngineUtil.restoreHiddenBlock(location);
            }
            point.getLocation().getBlock().setType(point.getPlaceholder(), false);
        }
        save();
        return true;
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public int getCycleIntervalSeconds() {
        return cycleIntervalSeconds;
    }

    /**
     * Returns true if the given location is a registered auto-scan point AND it
     * is currently in the inactive (placeholder) state. Used by BlockBreakListener
     * to prevent breaking the placeholder block.
     */
    public boolean isInactivePoint(Location location) {
        if (location == null) return false;
        AutoScanPoint point = points.get(buildKey(location));
        return point != null && !point.isActive();
    }

    /**
     * Returns true if the location is a registered auto-scan point (active or inactive).
     */
    public boolean isRegisteredPoint(Location location) {
        if (location == null) return false;
        return points.containsKey(buildKey(location));
    }

    public int getPointCount() {
        return points.size();
    }

    public Collection<AutoScanPoint> getAllPoints() {
        return Collections.unmodifiableCollection(points.values());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private double getActiveChance(String blockIdentifier) {
        // Per-block chance can be set in blocks.yml under auto-scan.active-chance
        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier);
        if (data != null && data.getAutoScanActiveChance() >= 0) {
            return data.getAutoScanActiveChance();
        }
        return defaultActiveChance;
    }

    private void placeOreBlock(AutoScanPoint point) {
        Location loc = point.getLocation();
        String blockId = point.getBlockIdentifier();

        if (blockId.toLowerCase().startsWith("nexo:") && BlockRegen.nexoEnabled) {
            NexoUtil.placeNexoBlock(blockId, loc);
            return;
        }

        // ItemsAdder blocks would need special placement — for now fall back to vanilla
        if (blockId.contains(":")) {
            // Custom block without dedicated placement support: skip silently
            plugin.getLogger().warning("[AutoScan] Cannot place custom block '" + blockId + "' — only Nexo blocks support auto-placement. Keeping placeholder.");
            return;
        }

        try {
            Material mat = Material.valueOf(blockId.toUpperCase());
            loc.getBlock().setType(mat, false);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[AutoScan] Unknown material '" + blockId + "' for auto-scan point. Skipping.");
        }
    }

    private void restoreOreBlock(AutoScanPoint point) {
        // When a point is unregistered, restore it to the ore state
        placeOreBlock(point);
    }

    /**
     * Spawn model untuk blok di lokasi, jika block tersebut punya konfigurasi Model Engine.
     * Hanya dipanggil jika ModelEngine aktif.
     */
    private void spawnModelForBlock(String blockIdentifier, Location location) {
        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier);
        if (data == null || !data.hasModelEngine()) return;
        ModelEngineUtil.spawnModel(
                location,
                data.getModelEngineId(),
                data.getModelYaw(),
                data.getModelHeightOffset(),
                data.isModelHideBlock()
        );
    }

    // -------------------------------------------------------------------------
    // Region Scan
    // -------------------------------------------------------------------------

    /**
     * Result object returned by {@link #scanRegion(me.allync.blockregen.data.Region)}.
     */
    public static class ScanResult {
        public final int added;
        public final int skipped;   // already registered
        public final int total;     // total regen blocks found

        public ScanResult(int added, int skipped, int total) {
            this.added   = added;
            this.skipped = skipped;
            this.total   = total;
        }
    }

    /**
     * Scans every block inside a BlockRegen internal {@code region} and registers any
     * configured regen block that is not already an auto-scan point.
     *
     * @param region the BlockRegen {@link me.allync.blockregen.data.Region} to scan.
     * @return a {@link ScanResult} describing how many blocks were found/added/skipped.
     */
    public ScanResult scanRegion(me.allync.blockregen.data.Region region) {
        World world = region.getWorld();
        if (world == null) return new ScanResult(0, 0, 0);
        Location min = region.getMinPoint();
        Location max = region.getMaxPoint();
        return scanBoundingBox(world,
                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ());
    }

    /**
     * Scans every block inside a WorldGuard region (by name) across all loaded worlds.
     * Falls back gracefully when WorldGuard is disabled.
     *
     * @param regionName the WorldGuard region ID (case-insensitive).
     * @return a {@link ScanResult}, or {@code null} if the WG region was not found in any world.
     */
    public ScanResult scanWorldGuardRegion(String regionName) {
        if (!plugin.getConfigManager().worldGuardEnabled || plugin.getWorldGuardPlugin() == null) {
            return null;
        }
        for (World world : Bukkit.getWorlds()) {
            try {
                RegionManager wgRM = WorldGuard.getInstance()
                        .getPlatform()
                        .getRegionContainer()
                        .get(BukkitAdapter.adapt(world));
                if (wgRM == null) continue;

                ProtectedRegion pr = wgRM.getRegion(regionName.toLowerCase());
                if (pr == null) continue;

                BlockVector3 min = pr.getMinimumPoint();
                BlockVector3 max = pr.getMaximumPoint();
                return scanBoundingBox(world,
                        min.x(), min.y(), min.z(),
                        max.x(), max.y(), max.z());
            } catch (Exception ignored) {
                // Keep iterating other worlds if one fails
            }
        }
        return null; // not found in any world
    }

    /**
     * Core implementation: iterates every block in the given axis-aligned bounding box
     * and registers any configured regen block not already tracked.
     */
    private ScanResult scanBoundingBox(World world,
                                       int minX, int minY, int minZ,
                                       int maxX, int maxY, int maxZ) {
        int added   = 0;
        int skipped = 0;
        int total   = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);

                    String blockId = plugin.getMiningManager().getBlockIdentifier(block);
                    if (!plugin.getBlockManager().isRegenBlock(blockId)) continue;

                    total++;
                    Location loc = block.getLocation();
                    String key = buildKey(loc);

                    if (points.containsKey(key)) {
                        skipped++;
                        continue;
                    }

                    BlockData data = plugin.getBlockManager().getBlockData(blockId);
                    Material placeholder = (data != null) ? data.getReplacedBlock() : defaultPlaceholder;

                    AutoScanPoint point = new AutoScanPoint(blockId, loc, placeholder, true);
                    points.put(key, point);
                    added++;
                }
            }
        }

        if (added > 0) {
            save();
        }

        return new ScanResult(added, skipped, total);
    }

    private String buildKey(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }
}
