package me.allync.blockregen.listener;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.data.MiningProgressState;
import me.allync.blockregen.data.MiningTargetKey;
import me.allync.blockregen.data.ToolRequirement;
import me.allync.blockregen.manager.MiningManager;
import me.allync.blockregen.task.PlayerMiningTask;
import me.allync.blockregen.util.ItemUtil;
import me.allync.blockregen.util.ModelEngineUtil;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Menangani interaksi player dengan entity model dari Model Engine.
 *
 * Pendekatan utama: PlayerAnimationEvent (ARM_SWING) + getTargetEntity() server-side.
 * Ini lebih reliable karena:
 * - EntityDamageByEntityEvent tidak terpicu untuk invisible entity di beberapa versi
 * - getTargetEntity() menggunakan server-side raycasting, bukan client-side visibility
 *
 * hide-block=true  : Block = AIR, mining via hit entity model
 * hide-block=false : Block ada, entity forward hit ke BlockMiningListener/BlockBreakListener
 */
public class ModelEngineHitListener implements Listener {

    private static final int REACH = 5;

    private final BlockRegen plugin;
    private final MiningManager miningManager;

    private final Map<UUID, PlayerMiningTask> activeTasks = new HashMap<>();
    private final Map<MiningTargetKey, UUID> activeMiners = new HashMap<>();
    private final Map<MiningTargetKey, MiningProgressState> persistedProgress = new HashMap<>();

    public ModelEngineHitListener(BlockRegen plugin) {
        this.plugin = plugin;
        this.miningManager = plugin.getMiningManager();
    }

    // -------------------------------------------------------------------------
    // 1. Cegah entity model mati — HANYA cancel damage
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand as)) return;
        if (!as.getScoreboardTags().contains("blockregen_model")) return;
        event.setCancelled(true);
        // Jangan lakukan apapun lagi di sini.
        // Mining ditangani oleh onPlayerAnimation di bawah.
    }

    // -------------------------------------------------------------------------
    // 2. Trigger utama: ARM_SWING + server-side entity targeting
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!plugin.getConfigManager().allWorldsEnabled
                && !plugin.getConfigManager().enabledWorlds.contains(player.getWorld().getName())) return;

        // Cari model entity yang ditarget player (server-side, bisa detect invisible entity)
        ArmorStand modelEntity = findTargetModelEntity(player);
        if (modelEntity == null) return;

        Location blockLoc = ModelEngineUtil.getBlockLocationFromEntity(modelEntity.getUniqueId());
        if (blockLoc == null) return;

        BlockState hiddenState = ModelEngineUtil.getHiddenBlockState(blockLoc);

        if (hiddenState == null) {
            // ---- hide-block: false — block masih ada ----
            // Forward arm swing ke block mining system via synthetic event
            handleNonHideBlock(player, blockLoc);
        } else {
            // ---- hide-block: true — block = AIR ----
            handleHideBlockSwing(player, blockLoc, hiddenState);
        }
    }

    // -------------------------------------------------------------------------
    // Cari model entity yang ditarget player
    // -------------------------------------------------------------------------

    /**
     * Mencari ArmorStand model BlockRegen yang ditarget player.
     * Strategi berlapis:
     * 1. getTargetEntity() - server-side raycast, detect invisible entities
     * 2. Scan entity dalam radius 1 blok dari target raycast (untuk bone entities)
     * 3. Scan entity dalam radius 2 blok dari player's target location
     */
    private ArmorStand findTargetModelEntity(Player player) {
        // --- Strategi 1: direct entity target ---
        try {
            Entity direct = player.getTargetEntity(REACH);
            if (direct instanceof ArmorStand as && as.getScoreboardTags().contains("blockregen_model")) {
                return as;
            }
        } catch (Exception ignored) {}

        // --- Strategi 2: Cari berdasarkan target block/location ---
        // Kalau player melihat ke lokasi dimana ada model (mungkin kena bone entity / air)
        Location eyeLoc = player.getEyeLocation();
        Location targetLoc = eyeLoc.clone().add(eyeLoc.getDirection().normalize().multiply(REACH));

        // Scan entity di sekitar lokasi target player
        Block targetBlock;
        try {
            targetBlock = player.getTargetBlockExact(REACH);
        } catch (Exception ignored) {
            targetBlock = null;
        }

        Location scanCenter = (targetBlock != null) ? targetBlock.getLocation() : targetLoc;

        for (Entity nearby : scanCenter.getWorld().getNearbyEntities(scanCenter, 1.5, 1.5, 1.5)) {
            if (nearby instanceof ArmorStand as && as.getScoreboardTags().contains("blockregen_model")) {
                // Pastikan player dalam jangkauan entity ini
                if (player.getLocation().distanceSquared(as.getLocation()) <= REACH * REACH) {
                    return as;
                }
            }
        }

        // --- Strategi 3: Cari dari active tasks milik player (sudah mining) ---
        PlayerMiningTask activeTask = activeTasks.get(player.getUniqueId());
        if (activeTask != null) {
            Location taskLoc = activeTask.getBlockLocation();
            if (taskLoc != null && player.getLocation().distanceSquared(taskLoc) <= REACH * REACH) {
                // Player sudah mining blok ini, tidak perlu cari entity lagi
                // Return dummy signal: null tapi kita handle via existing task di handleHideBlockSwing
                return findBaseEntityAt(taskLoc);
            }
        }

        return null;
    }

    /** Cari ArmorStand model di lokasi blok tertentu. */
    private ArmorStand findBaseEntityAt(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return null;
        Location center = blockLoc.clone().add(0.5, 0.5, 0.5);
        for (Entity e : blockLoc.getWorld().getNearbyEntities(center, 1.0, 1.5, 1.0)) {
            if (e instanceof ArmorStand as && as.getScoreboardTags().contains("blockregen_model")) {
                return as;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Handle hide-block=true: mining via entity hit
    // -------------------------------------------------------------------------

    private void handleHideBlockSwing(Player player, Location blockLoc, BlockState hiddenState) {
        UUID uuid = player.getUniqueId();
        MiningTargetKey blockKey = MiningTargetKey.from(blockLoc);

        // Cek task yang sudah ada
        PlayerMiningTask existingTask = activeTasks.get(uuid);
        if (existingTask != null) {
            if (existingTask.getBlockLocation().equals(blockLoc)) {
                // Masih mining blok yang sama: register hit, jaga progress
                existingTask.registerHit();
                return;
            } else {
                // Pindah ke blok lain: cancel task lama
                existingTask.cancelTask();
            }
        }

        // Cek blok sedang dimining orang lain
        UUID ownerUuid = activeMiners.get(blockKey);
        if (ownerUuid != null && !ownerUuid.equals(uuid)) {
            if (activeTasks.containsKey(ownerUuid)) {
                player.sendMessage(plugin.getConfigManager().blockBeingMinedMessage);
                return;
            }
            activeMiners.remove(blockKey);
        }

        // Cek region
        Set<String> regionNames = plugin.getRegionManager().getRegionNamesAt(blockLoc);
        boolean inRegion = plugin.getConfigManager().worldGuardEnabled
                ? plugin.getRegionManager().isLocationInAnySupportedRegion(blockLoc)
                : plugin.getRegionManager().isLocationInRegion(blockLoc);
        if (!inRegion) return;

        if (plugin.getRegenManager().isRegenerating(blockLoc)) return;

        // Ambil block data dari hidden state
        String blockIdentifier = hiddenState.getType().name();
        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier, regionNames);
        if (data == null) return;

        if (!checkTools(player, data, blockIdentifier, blockLoc)) return;

        miningManager.debug(player, blockIdentifier, "[ModelHit] Memulai/lanjut mining via ARM_SWING.");

        Block block = blockLoc.getBlock(); // = AIR

        if (!data.hasCustomBreakDuration()) {
            // Instant break
            ModelEngineUtil.removeModel(blockLoc);
            ModelEngineUtil.restoreHiddenBlock(blockLoc);
            BlockState restoredState = blockLoc.getBlock().getState();
            miningManager.processBlockBreak(player, block, data, restoredState, blockIdentifier);
            return;
        }

        // Custom duration — buat task
        cleanupExpiredProgress(blockKey);
        long resumedMs = 0L;
        MiningProgressState saved = persistedProgress.get(blockKey);
        if (saved != null) resumedMs = saved.getElapsedMs();

        activeMiners.put(blockKey, uuid);
        PlayerMiningTask task = new PlayerMiningTask(
                plugin, player, block, data, blockIdentifier,
                activeTasks, activeMiners, persistedProgress,
                blockKey, resumedMs,
                hiddenState
        );
        task.runTaskTimer(plugin, 0L, 1L);
        activeTasks.put(uuid, task);
    }

    // -------------------------------------------------------------------------
    // Handle hide-block=false: forward ke block system
    // -------------------------------------------------------------------------

    private void handleNonHideBlock(Player player, Location blockLoc) {
        Block realBlock = blockLoc.getBlock();
        if (realBlock.getType() == Material.AIR) return;

        Set<String> regionNames = plugin.getRegionManager().getRegionNamesAt(blockLoc);
        String blockIdentifier = miningManager.getBlockIdentifier(realBlock);
        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier, regionNames);
        if (data == null) return;

        if (data.hasCustomBreakDuration()) {
            // Forward ke BlockMiningListener via synthetic BlockDamageEvent
            org.bukkit.event.block.BlockDamageEvent syntheticBDE =
                    new org.bukkit.event.block.BlockDamageEvent(
                            player, realBlock, player.getInventory().getItemInMainHand(), false);
            plugin.getServer().getPluginManager().callEvent(syntheticBDE);
        } else {
            // Forward ke BlockBreakListener via synthetic BlockBreakEvent
            org.bukkit.event.block.BlockBreakEvent syntheticBBE =
                    new org.bukkit.event.block.BlockBreakEvent(realBlock, player);
            plugin.getServer().getPluginManager().callEvent(syntheticBBE);
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelFor(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getWorld() == null || event.getTo().getWorld() == null) return;
        if (!event.getFrom().getWorld().getUID().equals(event.getTo().getWorld().getUID())) {
            cancelFor(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        cancelFor(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean checkTools(Player player, BlockData data, String blockIdentifier, Location blockLoc) {
        double power = data.requiresPickaxePower()
                ? ItemUtil.getPickaxePower(player.getInventory().getItemInMainHand()) : 0.0;

        if (data.requiresTool()) {
            ItemStack item = player.getInventory().getItemInMainHand();
            boolean matches = false;
            for (ToolRequirement req : data.getRequiredTools()) {
                if (req.matches(item)) { matches = true; break; }
            }
            if (!matches && data.requiresPickaxePower() && power >= data.getRequirePickaxePower()) {
                matches = true;
            }
            if (!matches) {
                player.sendMessage(plugin.getConfigManager().wrongToolMessage
                        .replace("%tool%", miningManager.formatRequiredTools(data)));
                SoundUtil.playSoundToPlayer(player, blockLoc, plugin.getConfigManager().wrongToolSound, null);
                return false;
            }
        }

        if (data.requiresPickaxePower() && power < data.getRequirePickaxePower()) {
            player.sendMessage(plugin.getConfigManager().lowPickaxePowerMessage
                    .replace("%power%", String.valueOf((int) data.getRequirePickaxePower()))
                    .replace("%your_power%", String.format("%.1f", power)));
            SoundUtil.playSoundToPlayer(player, blockLoc, plugin.getConfigManager().wrongToolSound, null);
            return false;
        }
        return true;
    }

    private void cleanupExpiredProgress(MiningTargetKey key) {
        MiningProgressState state = persistedProgress.get(key);
        if (state == null) return;
        if (System.currentTimeMillis() - state.getUpdatedAtMs()
                > plugin.getConfigManager().miningResumeTimeoutMs) {
            persistedProgress.remove(key);
        }
    }

    private void cancelFor(UUID uuid) {
        PlayerMiningTask task = activeTasks.get(uuid);
        if (task != null) task.cancelTask();
    }

    /**
     * Batalkan task mining di lokasi ini secara langsung.
     * Dipanggil oleh MiningManager.cancelMiningAt() saat cycle/relocate.
     */
    public void cancelTaskAt(Location loc) {
        if (loc == null) return;
        MiningTargetKey key = MiningTargetKey.from(loc);
        UUID miner = activeMiners.get(key);
        if (miner != null) {
            PlayerMiningTask task = activeTasks.get(miner);
            if (task != null) task.cancelTask();
        }
        persistedProgress.remove(key);
        activeMiners.remove(key);
    }

    public void shutdown() {
        activeTasks.values().forEach(t -> { if (t != null) t.cancelTask(); });
        activeTasks.clear();
        activeMiners.clear();
        persistedProgress.clear();
    }
}
