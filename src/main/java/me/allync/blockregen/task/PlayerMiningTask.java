package me.allync.blockregen.task;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.data.MiningProgressState;
import me.allync.blockregen.data.MiningTargetKey;
import me.allync.blockregen.manager.MiningManager;
import me.allync.blockregen.util.BreakDurationHologramUtil;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

/**
 * A per-player task that manages the custom breaking timer, animations,
 * and completion of a custom-duration block.
 */
public class PlayerMiningTask extends BukkitRunnable {

    private static final int SWING_INTERVAL_TICKS = 20;

    private final BlockRegen plugin;
    private final MiningManager miningManager;
    private final Player player;
    private final Block block;
    private final BlockData data;
    private final String blockIdentifier;
    private final BlockState originalState;
    private final long startTime;
    private final long requiredTimeMs;
    private final Map<UUID, PlayerMiningTask> miningTasks;
    private final Map<MiningTargetKey, UUID> blockMiners;
    private final Map<MiningTargetKey, MiningProgressState> persistedProgress;
    private final MiningTargetKey blockKey;
    private final ItemStack initialToolSnapshot;
    private long lastManualHitMs;
    private long accumulatedElapsedMs;
    private long lastProgressTickMs;
    private long lastOnTargetMs;

    private int currentStage = -1;
    private int swingTickCounter = 0;
    private boolean stateCleared = false;

    public PlayerMiningTask(
            BlockRegen plugin,
            Player player,
            Block block,
            BlockData data,
            String blockIdentifier,
            Map<UUID, PlayerMiningTask> miningTasks,
            Map<MiningTargetKey, UUID> blockMiners,
            Map<MiningTargetKey, MiningProgressState> persistedProgress,
            MiningTargetKey blockKey,
            long resumedElapsedMs
    ) {
        this.plugin = plugin;
        this.miningManager = plugin.getMiningManager();
        this.player = player;
        this.block = block;
        this.data = data;
        this.blockIdentifier = blockIdentifier;
        this.originalState = block.getState();
        this.miningTasks = miningTasks;
        this.blockMiners = blockMiners;
        this.persistedProgress = persistedProgress;
        this.blockKey = blockKey;
        this.initialToolSnapshot = normalizeItem(player.getInventory().getItemInMainHand());
        this.lastManualHitMs = System.currentTimeMillis();
        this.accumulatedElapsedMs = Math.max(0L, resumedElapsedMs);
        this.lastProgressTickMs = System.currentTimeMillis();
        this.lastOnTargetMs = this.lastProgressTickMs;

        this.startTime = System.currentTimeMillis();
        this.requiredTimeMs = Math.max(50L, miningManager.calculateRequiredBreakTimeMs(player, data));
        miningManager.markMining(this.block.getLocation());
    }

    @Override
    public void run() {
        // 1. Check if task should be cancelled (Player offline)
        if (!player.isOnline()) {
            BreakDurationHologramUtil.remove(player);
            cancelTask();
            return;
        }

        String currentIdentifier = miningManager.getBlockIdentifier(block);
        if (currentIdentifier == null || !currentIdentifier.equalsIgnoreCase(blockIdentifier)) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (Block already changed).");
            clearTaskState(true, false);
            return;
        }

        // 2. Check if player is still looking at the same block
        Block targetBlock = null;
        try {
            targetBlock = player.getTargetBlockExact(5);
        } catch (IllegalStateException e) {
            // Ignore if player is looking at air.
        }

        long now = System.currentTimeMillis();
        boolean isOnTarget = targetBlock != null && targetBlock.getLocation().equals(block.getLocation());
        if (!isOnTarget) {
            BreakDurationHologramUtil.remove(player);
            lastProgressTickMs = now;
            if (now - lastOnTargetMs >= plugin.getConfigManager().miningReleaseGraceMs) {
                miningManager.debug(player, blockIdentifier, "&cCancelling mining (Block released for too long).");
                cancelTask();
                return;
            }
            return;
        }
        lastOnTargetMs = now;

        // 3. Check if player is still in GameMode SURVIVAL
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (Gamemode changed).");
            BreakDurationHologramUtil.remove(player);
            cancelTask();
            return;
        }

        // Cancel mining if player switches tool while mining.
        if (hasToolChanged()) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (Tool changed).");
            cancelTask();
            return;
        }

        // Require repeated clicks so players cannot complete mining by holding click once.
        if (now - lastManualHitMs > plugin.getConfigManager().miningHoldMineTimeoutMs) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (Hold mining is disabled, keep clicking).");
            cancelTask();
            return;
        }

        // Cancel mining if the player stops holding a valid mining tool.
        if (!isHoldingPickaxe()) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (No pickaxe in main hand).");
            String requiredTools = miningManager.formatRequiredTools(data);
            player.sendMessage(plugin.getConfigManager().wrongToolMessage.replace("%tool%", requiredTools));
            cancelTask();
            return;
        }

        // 4. Player is still mining. Calculate progress.
        long delta = Math.max(0L, now - lastProgressTickMs);
        accumulatedElapsedMs += delta;
        lastProgressTickMs = now;

        float progress = (float) accumulatedElapsedMs / (float) requiredTimeMs;
        double remainingSeconds = Math.max(0.0D, (requiredTimeMs - accumulatedElapsedMs) / 1000.0D);
        BreakDurationHologramUtil.update(player, block.getLocation(), remainingSeconds, plugin);
        playWaitingSwingAnimation();

        if (progress >= 1.0f) {
            miningManager.debug(player, blockIdentifier, "Block break finished.");

            sendSafeBlockDamage(1.0f);

            miningManager.processBlockBreak(player, block, data, originalState, blockIdentifier);

            clearTaskState(true, false);

        } else {
            int newStage = (int) (progress * 10.0f);

            if (newStage != this.currentStage) {
                this.currentStage = newStage;
                sendSafeBlockDamage(progress);
            }
        }
    }

    /**
     * Membatalkan task dan membersihkan.
     */
    public void cancelTask() {
        clearTaskState(true, true);
    }

    private void clearTaskState(boolean resetCrackAnimation, boolean saveProgress) {
        if (stateCleared) {
            return;
        }
        stateCleared = true;

        if (!this.isCancelled()) {
            this.cancel();
        }
        miningTasks.remove(player.getUniqueId());
        blockMiners.computeIfPresent(blockKey, (key, owner) -> owner.equals(player.getUniqueId()) ? null : owner);

        if (saveProgress && accumulatedElapsedMs > 0L && accumulatedElapsedMs < requiredTimeMs) {
            persistedProgress.put(blockKey, new MiningProgressState(accumulatedElapsedMs, System.currentTimeMillis()));
        } else {
            persistedProgress.remove(blockKey);
        }

        BreakDurationHologramUtil.remove(player);
        SoundUtil.stopSoundToPlayer(player, data.getBreakSound(), plugin.getConfigManager().defaultBreakSound);
        miningManager.unmarkMining(block.getLocation());

        if (player.isOnline()) {
            if (resetCrackAnimation) {
                sendSafeBlockDamage(0.0f);
            }
        }
    }

    private void playWaitingSwingAnimation() {
        swingTickCounter++;
        if (swingTickCounter >= SWING_INTERVAL_TICKS) {
            swingTickCounter = 0;
            player.swingMainHand();
        }
    }

    private boolean isHoldingPickaxe() {
        if (data.requiresTool()) {
            for (var requirement : data.getRequiredTools()) {
                if (requirement.matches(player.getInventory().getItemInMainHand())) {
                    return true;
                }
            }
            return false;
        }

        Material material = player.getInventory().getItemInMainHand().getType();
        return material != Material.AIR && material.name().endsWith("_PICKAXE");
    }

    private boolean hasToolChanged() {
        ItemStack currentTool = normalizeItem(player.getInventory().getItemInMainHand());
        if (initialToolSnapshot == null && currentTool == null) {
            return false;
        }
        if (initialToolSnapshot == null || currentTool == null) {
            return true;
        }
        return !initialToolSnapshot.isSimilar(currentTool);
    }

    private ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return normalized;
    }

    public void registerHit() {
        this.lastManualHitMs = System.currentTimeMillis();
        this.lastOnTargetMs = this.lastManualHitMs;
    }

    private void sendSafeBlockDamage(float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        for (Player onlinePlayer : block.getWorld().getPlayers()) {
            onlinePlayer.sendBlockDamage(block.getLocation(), clamped);
        }
    }

    /**
     * Mendapatkan lokasi blok yang sedang ditambang task ini.
     * @return Lokasi Blok
     */
    public Location getBlockLocation() {
        return this.block.getLocation();
    }
}