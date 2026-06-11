package me.allync.blockregen.manager;

import dev.lone.itemsadder.api.CustomBlock;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.util.ModelEngineUtil;
import me.allync.blockregen.util.NexoUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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

    private void rotateIfIdle(Location location, String expectedConfiguredIdentifier) {
        if (location.getWorld() == null) {
            return;
        }

        if (!plugin.getConfigManager().idleRotateEnabled) {
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

        String nextIdentifier = selectNextVariant(currentData, currentIdentifier);
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

    private String selectNextVariant(BlockData data, String currentIdentifier) {
        List<BlockData.RegenVariant> variants = data.getRegenVariants();
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        double total = 0.0D;
        for (BlockData.RegenVariant variant : variants) {
            if (variant.getChance() > 0 && !variant.getBlockIdentifier().equalsIgnoreCase(currentIdentifier)) {
                total += variant.getChance();
            }
        }

        if (total <= 0.0D) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0D;
        for (BlockData.RegenVariant variant : variants) {
            if (variant.getChance() <= 0 || variant.getBlockIdentifier().equalsIgnoreCase(currentIdentifier)) {
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

