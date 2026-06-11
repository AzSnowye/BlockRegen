package me.allync.blockregen.manager;

import dev.lone.itemsadder.api.CustomBlock;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.util.ModelEngineUtil;
import me.allync.blockregen.util.NexoUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rotates regen blocks that stay unmined for too long.
 */
public class IdleRotationManager {

    private final BlockRegen plugin;
    private final Map<String, BukkitTask> pendingTasks = new HashMap<>();

    public IdleRotationManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public void schedule(Location location, String configuredIdentifier) {
        if (location == null || location.getWorld() == null || configuredIdentifier == null || configuredIdentifier.isEmpty()) {
            return;
        }

        cancel(location);

        double minutes = resolveIdleMinutes(configuredIdentifier);
        if (minutes <= 0.0D) {
            return;
        }

        long delayTicks = Math.max(20L, Math.round(minutes * 60.0D * 20.0D));
        String key = locationKey(location);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingTasks.remove(key);
            rotateIfIdle(location, configuredIdentifier);
        }, delayTicks);

        pendingTasks.put(key, task);
    }

    public void cancel(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        String key = locationKey(location);
        BukkitTask task = pendingTasks.remove(key);
        if (task != null) {
            task.cancel();
        }
    }

    public void clear() {
        for (BukkitTask task : pendingTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        pendingTasks.clear();
    }

    /**
     * Force-rotate a block now, regardless of idle timeout.
     *
     * @param location target block location
     * @param requiredRegionName optional region name filter (null/blank = no filter)
     * @param requiredConfiguredIdentifier optional configured id filter (null/blank = no filter)
     * @return applied configured identifier after rotate, or null if rotate was not possible
     */
    public String forceRotateAt(Location location, String requiredRegionName, String requiredConfiguredIdentifier) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        cancel(location);

        Block block = location.getBlock();
        Collection<String> regionNames = plugin.getRegionManager().getRegionNamesAt(location);

        if (requiredRegionName != null && !requiredRegionName.isEmpty()) {
            String normalized = requiredRegionName.toLowerCase(Locale.ROOT);
            if (!regionNames.contains(normalized)) {
                return null;
            }
        }

        String currentIdentifier = plugin.getMiningManager().getBlockIdentifier(block, regionNames);
        if (currentIdentifier == null) {
            return null;
        }

        if (requiredConfiguredIdentifier != null && !requiredConfiguredIdentifier.isEmpty()
                && !currentIdentifier.equalsIgnoreCase(requiredConfiguredIdentifier)) {
            return null;
        }

        BlockData currentData = plugin.getBlockManager().getBlockData(currentIdentifier, regionNames);
        if (currentData == null || !currentData.hasRegenVariants()) {
            return null;
        }

        String currentWorldIdentifier = getWorldBlockIdentifier(block);
        String nextIdentifier = selectNextVariant(currentData, currentWorldIdentifier, currentIdentifier);
        if (nextIdentifier == null || nextIdentifier.isEmpty()) {
            return null;
        }

        if (!placeIdentifier(nextIdentifier, location)) {
            return null;
        }

        String appliedIdentifier = plugin.getMiningManager().getBlockIdentifier(block, regionNames);
        if (appliedIdentifier != null && plugin.getBlockManager().isRegenBlockInRegion(appliedIdentifier, regionNames)) {
            schedule(location, appliedIdentifier);
            return appliedIdentifier;
        }

        return null;
    }

    private void rotateIfIdle(Location location, String expectedConfiguredIdentifier) {
        if (location.getWorld() == null) {
            return;
        }

        double minutes = resolveIdleMinutes(expectedConfiguredIdentifier);
        if (minutes <= 0.0D) {
            return;
        }

        if (plugin.getRandomOreManager().isEnabled() && plugin.getRandomOreManager().isManagedPoint(location)) {
            return;
        }
        if (plugin.getAutoScanManager() != null && plugin.getAutoScanManager().isRegisteredPoint(location)) {
            return;
        }

        if (plugin.getRegenManager().isRegenerating(location) || plugin.getMiningManager().isBeingMined(location)) {
            schedule(location, expectedConfiguredIdentifier);
            return;
        }

        Block block = location.getBlock();
        Collection<String> regionNames = plugin.getRegionManager().getRegionNamesAt(location);
        String currentIdentifier = plugin.getMiningManager().getBlockIdentifier(block, regionNames);
        if (currentIdentifier == null || !currentIdentifier.equalsIgnoreCase(expectedConfiguredIdentifier)) {
            return;
        }

        BlockData currentData = plugin.getBlockManager().getBlockData(currentIdentifier, regionNames);
        if (currentData == null || !currentData.hasRegenVariants()) {
            return;
        }

        String currentWorldIdentifier = getWorldBlockIdentifier(block);
        String nextIdentifier = selectNextVariant(currentData, currentWorldIdentifier, currentIdentifier);
        if (nextIdentifier == null || nextIdentifier.isEmpty()) {
            return;
        }

        if (!placeIdentifier(nextIdentifier, location)) {
            return;
        }

        String appliedIdentifier = plugin.getMiningManager().getBlockIdentifier(block, regionNames);
        if (appliedIdentifier != null && plugin.getBlockManager().isRegenBlockInRegion(appliedIdentifier, regionNames)) {
            schedule(location, appliedIdentifier);
        }
    }

    /**
     * Scans all defined regions and schedules idle rotation for all regen blocks found.
     */
    public void scheduleAll() {
        plugin.getLogger().info("[IdleRotation] Scanning regions for existing regen blocks...");
        int count = 0;

        for (me.allync.blockregen.data.Region region : plugin.getRegionManager().getRegions()) {
            Location min = region.getMinPoint();
            Location max = region.getMaxPoint();
            World world = region.getWorld();
            if (world == null) continue;

            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                    for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        Block block = world.getBlockAt(x, y, z);
                        Collection<String> regions = plugin.getRegionManager().getRegionNamesAt(block.getLocation());
                        String identifier = plugin.getMiningManager().getBlockIdentifier(block, regions);
                        
                        if (identifier != null && plugin.getBlockManager().isRegenBlockInRegion(identifier, regions)) {
                            schedule(block.getLocation(), identifier);
                            count++;
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("[IdleRotation] Scheduled " + count + " existing blocks for idle rotation.");
    }

    private String selectNextVariant(BlockData data, String currentWorldIdentifier, String currentConfiguredIdentifier) {
        List<BlockData.RegenVariant> variants = data.getRegenVariants();
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        double total = 0.0D;
        for (BlockData.RegenVariant variant : variants) {
            if (variant.getChance() > 0 && !isCurrentVariant(variant.getBlockIdentifier(), currentWorldIdentifier, currentConfiguredIdentifier)) {
                total += variant.getChance();
            }
        }

        if (total <= 0.0D) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0D;
        for (BlockData.RegenVariant variant : variants) {
            if (variant.getChance() <= 0 || isCurrentVariant(variant.getBlockIdentifier(), currentWorldIdentifier, currentConfiguredIdentifier)) {
                continue;
            }
            cumulative += variant.getChance();
            if (roll < cumulative) {
                return variant.getBlockIdentifier();
            }
        }

        return null;
    }

    private double resolveIdleMinutes(String configuredIdentifier) {
        BlockData data = plugin.getBlockManager().getBlockData(configuredIdentifier);
        if (data == null) {
            return -1.0D;
        }

        double blockMinutes = data.getIdleRotateMinutes();
        if (blockMinutes > 0.0D) {
            return blockMinutes;
        }

        if (!plugin.getConfigManager().idleRotateEnabled) {
            return -1.0D;
        }

        return plugin.getConfigManager().idleRotateDefaultMinutes;
    }

    private boolean isCurrentVariant(String variantIdentifier, String currentWorldIdentifier, String currentConfiguredIdentifier) {
        if (variantIdentifier == null) {
            return false;
        }
        if (currentWorldIdentifier != null && variantIdentifier.equalsIgnoreCase(currentWorldIdentifier)) {
            return true;
        }
        return currentConfiguredIdentifier != null && variantIdentifier.equalsIgnoreCase(currentConfiguredIdentifier);
    }

    private String getWorldBlockIdentifier(Block block) {
        if (block == null) {
            return "";
        }

        String nexoId = BlockRegen.nexoEnabled ? NexoUtil.getNexoBlockId(block) : null;
        if (nexoId != null && !nexoId.isEmpty()) {
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

    private boolean placeIdentifier(String blockIdentifier, Location location) {
        boolean placed = false;

        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier);
        if (data != null) {
            String actualBlockId = data.getBlockId();
            if (actualBlockId != null && !actualBlockId.isEmpty()) {
                String lower = actualBlockId.toLowerCase(Locale.ROOT);
                if (lower.startsWith("nexo:")) {
                    placed = BlockRegen.nexoEnabled && NexoUtil.placeNexoBlock(actualBlockId, location);
                } else if (actualBlockId.contains(":")) {
                    if (BlockRegen.itemsAdderEnabled) {
                        try {
                            placed = CustomBlock.place(actualBlockId, location) != null;
                        } catch (Throwable ignored) {
                            placed = false;
                        }
                    }
                } else {
                    Material material = parseMaterial(actualBlockId);
                    if (material != null) {
                        location.getBlock().setType(material, false);
                        placed = true;
                    }
                }
            }
        }

        if (!placed) {
            if (blockIdentifier.toLowerCase(Locale.ROOT).startsWith("nexo:")) {
                placed = BlockRegen.nexoEnabled && NexoUtil.placeNexoBlock(blockIdentifier, location);
            } else if (blockIdentifier.contains(":")) {
                if (BlockRegen.itemsAdderEnabled) {
                    try {
                        placed = CustomBlock.place(blockIdentifier, location) != null;
                    } catch (Throwable ignored) {
                        placed = false;
                    }
                }
            } else {
                Material material = parseMaterial(blockIdentifier);
                if (material != null) {
                    location.getBlock().setType(material, false);
                    placed = true;
                }
            }
        }

        if (placed && BlockRegen.modelEngineEnabled && data != null && data.hasModelEngine()) {
            ModelEngineUtil.removeModel(location);
            ModelEngineUtil.restoreHiddenBlock(location);
            ModelEngineUtil.spawnModel(
                    location,
                    data.getModelEngineId(),
                    data.getModelYaw(),
                    data.getModelHeightOffset(),
                    data.isModelHideBlock()
            );
        }

        return placed;
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

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}


