package me.allync.blockregen.task;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.data.MiningProgressState;
import me.allync.blockregen.data.MiningTargetKey;
import me.allync.blockregen.manager.MiningManager;
import me.allync.blockregen.util.BreakDurationHologramUtil;
import me.allync.blockregen.util.ModelEngineUtil;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player task untuk sistem penambangan kustom dengan durasi.
 *
 * Mendukung stackable blocks: jika ada blok berdampingan dengan tipe sama,
 * semua akan dibreak sekaligus saat penambangan selesai.
 *
 * Dipicu oleh RIGHT_CLICK_BLOCK (PlayerInteractEvent).
 */
public class PlayerMiningTask extends BukkitRunnable {

    private static final int SWING_INTERVAL_TICKS = 10; // lebih sering untuk feel right-click

    private final BlockRegen plugin;
    private final MiningManager miningManager;
    private final Player player;
    private final Block block;
    private final BlockData data;
    private final String blockIdentifier;
    private final BlockState originalState;
    private final long requiredTimeMs;
    private final Map<UUID, PlayerMiningTask> miningTasks;
    private final Map<MiningTargetKey, UUID> blockMiners;
    private final Map<MiningTargetKey, MiningProgressState> persistedProgress;
    private final MiningTargetKey blockKey;
    private final ItemStack initialToolSnapshot;
    private final boolean isHideBlockMode;
    /** Daftar blok stackable (termasuk blok utama di index 0). */
    private final List<Block> stackedBlocks;

    private long lastManualHitMs;
    private long accumulatedElapsedMs;
    private long lastProgressTickMs;
    private long lastOnTargetMs;

    private int currentStage = -1;
    private int swingTickCounter = 0;
    private boolean stateCleared = false;

    // ─── Constructor Lama (tanpa stacked, tanpa injectedState) ───────────────

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
        this(plugin, player, block, data, blockIdentifier, miningTasks, blockMiners,
                persistedProgress, blockKey, resumedElapsedMs, null, null);
    }

    // ─── Constructor dengan injectedOriginalState (hide-block, tanpa stacked) ─

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
            long resumedElapsedMs,
            BlockState injectedOriginalState
    ) {
        this(plugin, player, block, data, blockIdentifier, miningTasks, blockMiners,
                persistedProgress, blockKey, resumedElapsedMs, injectedOriginalState, null);
    }

    // ─── Constructor UTAMA ────────────────────────────────────────────────────

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
            long resumedElapsedMs,
            BlockState injectedOriginalState,
            List<Block> stackedBlocks
    ) {
        this.plugin = plugin;
        this.miningManager = plugin.getMiningManager();
        this.player = player;
        this.block = block;
        this.data = data;
        this.blockIdentifier = blockIdentifier;
        this.originalState = (injectedOriginalState != null) ? injectedOriginalState : block.getState();
        this.isHideBlockMode = (injectedOriginalState != null);
        this.miningTasks = miningTasks;
        this.blockMiners = blockMiners;
        this.persistedProgress = persistedProgress;
        this.blockKey = blockKey;
        this.initialToolSnapshot = normalizeItem(player.getInventory().getItemInMainHand());
        this.lastManualHitMs = System.currentTimeMillis();
        this.accumulatedElapsedMs = Math.max(0L, resumedElapsedMs);
        this.lastProgressTickMs = System.currentTimeMillis();
        this.lastOnTargetMs = this.lastProgressTickMs;
        this.requiredTimeMs = Math.max(50L, miningManager.calculateRequiredBreakTimeMs(player, data));

        // Salin daftar blok stackable; jika null, buat list kosong (hanya blok utama)
        if (stackedBlocks != null && !stackedBlocks.isEmpty()) {
            this.stackedBlocks = new ArrayList<>(stackedBlocks);
        } else {
            this.stackedBlocks = new ArrayList<>();
            this.stackedBlocks.add(block);
        }

        miningManager.markMining(this.block.getLocation());
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        // 1. Player online?
        if (!player.isOnline()) {
            BreakDurationHologramUtil.remove(player);
            cancelTask();
            return;
        }

        // 2. Blok masih valid?
        String currentIdentifier = miningManager.getBlockIdentifier(block);
        boolean blockStillValid;
        if (currentIdentifier != null && currentIdentifier.equalsIgnoreCase("AIR")) {
            blockStillValid = ModelEngineUtil.isHiddenBlock(block.getLocation())
                    && originalState.getType() != Material.AIR;
        } else {
            blockStillValid = currentIdentifier != null && currentIdentifier.equalsIgnoreCase(blockIdentifier);
        }
        if (!blockStillValid) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (Block already changed).");
            clearTaskState(true, false);
            return;
        }

        // 3. Pengecekan tambahan (jarak, gamemode, alat)
        long now = System.currentTimeMillis();
        boolean isOnTarget;
        if (isHideBlockMode) {
            double dist = player.getLocation().distance(block.getLocation().clone().add(0.5, 0.5, 0.5));
            isOnTarget = dist <= 5.0;
        } else {
            // Untuk mode otomatis, pemain harus terus melihat blok
            Block target = player.getTargetBlockExact(6);
            isOnTarget = target != null && target.getLocation().equals(block.getLocation());
        }

        if (!isOnTarget) {
            BreakDurationHologramUtil.remove(player);
            lastProgressTickMs = now;
            if (now - lastOnTargetMs >= plugin.getConfigManager().miningReleaseGraceMs) {
                miningManager.debug(player, blockIdentifier, "&cCancelling mining (Player too far).");
                cancelTask();
                return;
            }
            return;
        }
        lastOnTargetMs = now;

        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (Gamemode changed).");
            BreakDurationHologramUtil.remove(player);
            cancelTask();
            return;
        }

        if (hasToolChanged()) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (Tool changed).");
            cancelTask();
            return;
        }



        if (!isHoldingPickaxe()) {
            miningManager.debug(player, blockIdentifier, "&cCancelling mining (No valid tool in main hand).");
            String requiredTools = miningManager.formatRequiredTools(data);
            player.sendMessage(plugin.getConfigManager().wrongToolMessage.replace("%tool%", requiredTools));
            cancelTask();
            return;
        }

        // 4. Hitung progress
        long delta = Math.max(0L, now - lastProgressTickMs);
        accumulatedElapsedMs += delta;
        lastProgressTickMs = now;

        float progress = (float) accumulatedElapsedMs / (float) requiredTimeMs;

        // Update health bar hologram
        BreakDurationHologramUtil.update(player, block.getLocation(), progress, plugin);
        playWaitingSwingAnimation();

        if (progress >= 1.0f) {
            miningManager.debug(player, blockIdentifier, "Block break finished. Stack size: " + stackedBlocks.size());

            sendSafeBlockDamage(1.0f);

            if (isHideBlockMode) {
                ModelEngineUtil.removeModel(block.getLocation());
                ModelEngineUtil.restoreHiddenBlock(block.getLocation());
                miningManager.processBlockBreak(player, block, data, originalState, blockIdentifier);
            } else {
                // Hancurkan blok utama dulu
                breakBlock(block);
                // Hancurkan semua blok stackable lain (skip index 0 = blok utama)
                for (int i = 1; i < stackedBlocks.size(); i++) {
                    Block stacked = stackedBlocks.get(i);
                    // Pastikan blok masih ada dan belum berubah
                    String stackedId = miningManager.getBlockIdentifier(stacked);
                    if (stackedId.equalsIgnoreCase(blockIdentifier)
                            && !plugin.getRegenManager().isRegenerating(stacked.getLocation())) {
                        breakBlock(stacked);
                    }
                }
            }

            clearTaskState(true, false);
        } else {
            int newStage = (int) (progress * 10.0f);
            if (newStage != this.currentStage) {
                this.currentStage = newStage;
                sendSafeBlockDamage(progress);
            }
        }
    }

    /** Hancurkan satu blok melalui BlockBreakEvent (agar listener lain bisa hook). */
    private void breakBlock(Block target) {
        // Ambil state sebelum break
        BlockState state = target.getState();
        String id = miningManager.getBlockIdentifier(target);
        Set<String> regions = plugin.getRegionManager().getRegionNamesAt(target.getLocation());
        BlockData bd = plugin.getBlockManager().getBlockData(id, regions);
        if (bd == null) bd = data; // fallback ke data blok utama

        // Fire BlockBreakEvent dengan metadata agar BlockBreakListener tahu ini dari task
        target.setMetadata("blockregen-task-break", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        org.bukkit.event.block.BlockBreakEvent event = new org.bukkit.event.block.BlockBreakEvent(target, player);
        plugin.getServer().getPluginManager().callEvent(event);
        target.removeMetadata("blockregen-task-break", plugin);
    }


    public void cancelTask() {
        clearTaskState(true, true);
    }

    private void clearTaskState(boolean resetCrackAnimation, boolean saveProgress) {
        if (stateCleared) return;
        stateCleared = true;

        if (!this.isCancelled()) this.cancel();
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

        if (player.isOnline() && resetCrackAnimation) {
            sendSafeBlockDamage(0.0f);
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
        if (initialToolSnapshot == null && currentTool == null) return false;
        if (initialToolSnapshot == null || currentTool == null) return true;
        return !initialToolSnapshot.isSimilar(currentTool);
    }

    private ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return normalized;
    }

    /** Dipanggil tiap klik kanan untuk reset timer "hold-timeout". */
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

    public Location getBlockLocation() {
        return this.block.getLocation();
    }
}