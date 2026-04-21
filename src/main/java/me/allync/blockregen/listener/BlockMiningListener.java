package me.allync.blockregen.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.data.MiningProgressState;
import me.allync.blockregen.data.MiningTargetKey;
import me.allync.blockregen.data.ToolRequirement;
import me.allync.blockregen.manager.MiningManager;
import me.allync.blockregen.task.PlayerMiningTask;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listener for BlockDamageEvent to handle custom block breaking durations.
 * New Approach: Per-player task.
 */
public class BlockMiningListener implements Listener {

    private static final String TOUCH_PERMISSION_PREFIX = "blockregen.multiplier.block.";

    private final BlockRegen plugin;
    private final MiningManager miningManager;

    private final Map<UUID, PlayerMiningTask> activeMiningTasks = new HashMap<>();
    private final Map<MiningTargetKey, UUID> activeBlockMiners = new HashMap<>();
    private final Map<UUID, Long> mineConflictMessageCooldown = new HashMap<>();
    private final Map<MiningTargetKey, MiningProgressState> persistedProgress = new HashMap<>();
    private final Map<UUID, Map<MiningTargetKey, Long>> playerTouchedBlocks = new HashMap<>();

    public BlockMiningListener(BlockRegen plugin) {
        this.plugin = plugin;
        this.miningManager = plugin.getMiningManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockIdentifier = miningManager.getBlockIdentifier(block);
        Set<String> regionNames = plugin.getRegionManager().getRegionNamesAt(block.getLocation());

        // --- 1. Initial Checks ---
        if (player.getGameMode() != GameMode.SURVIVAL) return; // Only survival
        if (!plugin.getConfigManager().allWorldsEnabled && !plugin.getConfigManager().enabledWorlds.contains(player.getWorld().getName())) return;

        if (BlockRegen.harvestFlowEnabled) {
            Material blockType = block.getType();
            if (blockType == Material.WHEAT || blockType == Material.CARROTS ||
                    blockType == Material.POTATOES || blockType == Material.BEETROOTS ||
                    blockType == Material.NETHER_WART || blockType == Material.COCOA) {
                return;
            }
        }

        // --- 2. Get BlockData & Check if this listener should handle it ---
        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier, regionNames);
        if (data == null || !data.hasCustomBreakDuration()) {
            // BlockBreakListener will handle it.
            return;
        }

        // Stop vanilla breaking
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        MiningTargetKey blockKey = MiningTargetKey.from(block.getLocation());

        cleanupExpiredProgress(blockKey);

        // Check if player is already mining
        PlayerMiningTask existingTask = activeMiningTasks.get(uuid);
        if (existingTask != null) {
            // If player hits a *different* block, cancel the old task
            if (!existingTask.getBlockLocation().equals(block.getLocation())) {
                miningManager.debug(player, blockIdentifier, "&cSwitching target block. Cancelling old task.");
                existingTask.cancelTask();
                // Continue to create a new task below
            } else {
                // Player is hitting the same block, task is already running.
                // Refresh hit state; mining stops if player only holds click.
                existingTask.registerHit();
                return;
            }
        }

        if (!canPlayerTouchBlock(player, blockKey)) {
            player.sendMessage(plugin.getConfigManager().touchLimitReachedMessage);
            return;
        }

        UUID ownerUuid = activeBlockMiners.get(blockKey);
        if (ownerUuid != null && !ownerUuid.equals(uuid)) {
            PlayerMiningTask ownerTask = activeMiningTasks.get(ownerUuid);
            if (ownerTask == null) {
                // Stale lock from an interrupted task, recover automatically.
                activeBlockMiners.remove(blockKey);
            } else {
                maybeSendMineConflictMessage(player);
                return;
            }
        }

        // --- 3. WorldGuard Checks ---
        if (plugin.getConfigManager().worldGuardEnabled && plugin.getWorldGuardPlugin() != null) {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            boolean canBreak = query.testBuild(BukkitAdapter.adapt(block.getLocation()), localPlayer, Flags.BLOCK_BREAK);

            if (!canBreak) { // DENY
                if (!plugin.getConfigManager().worldGuardBreakRegenInDenyRegions) {
                    miningManager.debug(player, blockIdentifier, "WorldGuard check &cfailed&7 (block-break: DENY).");
                    return; // Respect DENY
                }
            } else { // ALLOW
                if (plugin.getConfigManager().worldGuardDisableOtherBreak) {
                    if (!plugin.getBlockManager().isRegenBlockInRegion(blockIdentifier, regionNames)) {
                        if (!plugin.isPlayerBypassing(player)) {
                            miningManager.debug(player, blockIdentifier, "Block is not a regen block. &cCancelling event due to WG config.");
                            return;
                        }
                    }
                }
            }
        }

        // --- 4. Further Checks (Region, Regenerating, Tool) ---
        boolean inSupportedRegion = plugin.getConfigManager().worldGuardEnabled
                ? plugin.getRegionManager().isLocationInAnySupportedRegion(block.getLocation())
                : plugin.getRegionManager().isLocationInRegion(block.getLocation());
        if (!inSupportedRegion) {
            miningManager.debug(player, blockIdentifier, "Location is not inside a supported region (BlockRegen/WorldGuard). &cIgnoring.");
            return;
        }

        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            miningManager.debug(player, blockIdentifier, "Block is already regenerating. &cCancelling event.");
            return;
        }

        if (data.requiresTool()) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            boolean toolMatches = false;
            for (ToolRequirement requirement : data.getRequiredTools()) {
                if (requirement.matches(itemInHand)) {
                    toolMatches = true;
                    break;
                }
            }
            if (!toolMatches) {
                miningManager.debug(player, blockIdentifier, "&cTool requirement not met.");
                String requiredTools = miningManager.formatRequiredTools(data);
                player.sendMessage(plugin.getConfigManager().wrongToolMessage.replace("%tool%", requiredTools));
                SoundUtil.playSoundToPlayer(player, block.getLocation(), plugin.getConfigManager().wrongToolSound, null);
                return;
            }
        }

        // --- 6. Create and Start New Task ---
        miningManager.debug(player, blockIdentifier, "Started mining. Total time: " + data.getBreakDuration() + "s");
        registerTouch(player, blockKey);

        long resumedElapsedMs = 0L;
        MiningProgressState savedState = persistedProgress.get(blockKey);
        if (savedState != null) {
            resumedElapsedMs = savedState.getElapsedMs();
        }

        activeBlockMiners.put(blockKey, uuid);
        PlayerMiningTask newTask = new PlayerMiningTask(
                plugin,
                player,
                block,
                data,
                blockIdentifier,
                activeMiningTasks,
                activeBlockMiners,
                persistedProgress,
                blockKey,
                resumedElapsedMs
        );
        newTask.runTaskTimer(plugin, 0L, 1L); // Run every 1 tick

        activeMiningTasks.put(uuid, newTask);
    }

    private void maybeSendMineConflictMessage(Player player) {
        long now = System.currentTimeMillis();
        long lastSent = mineConflictMessageCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastSent < 750L) {
            return;
        }
        mineConflictMessageCooldown.put(player.getUniqueId(), now);
        player.sendMessage(plugin.getConfigManager().blockBeingMinedMessage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        PlayerMiningTask activeTask = activeMiningTasks.get(player.getUniqueId());
        if (activeTask == null) {
            return;
        }

        Block targetBlock;
        try {
            targetBlock = player.getTargetBlockExact(5);
        } catch (IllegalStateException ignored) {
            return;
        }

        if (targetBlock != null && targetBlock.getLocation().equals(activeTask.getBlockLocation())) {
            activeTask.registerHit();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up data if player logs off
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerMiningTask existingTask = activeMiningTasks.get(uuid);
        if (existingTask != null) {
            existingTask.cancelTask();
        }
        mineConflictMessageCooldown.remove(uuid);
        playerTouchedBlocks.remove(uuid);
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        cancelTaskFor(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cancelTaskFor(event.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (!event.getFrom().getWorld().getUID().equals(event.getTo().getWorld().getUID())) {
            cancelTaskFor(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        cancelTaskFor(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (PlayerMiningTask task : activeMiningTasks.values()) {
            if (task != null) {
                task.cancelTask();
            }
        }
        activeMiningTasks.clear();
        activeBlockMiners.clear();
        mineConflictMessageCooldown.clear();
        playerTouchedBlocks.clear();
        persistedProgress.clear();
    }

    private void cancelTaskFor(UUID uuid) {
        PlayerMiningTask existingTask = activeMiningTasks.get(uuid);
        if (existingTask != null) {
            existingTask.cancelTask();
        }
        mineConflictMessageCooldown.remove(uuid);
        playerTouchedBlocks.remove(uuid);
    }

    private void cleanupExpiredProgress(MiningTargetKey blockKey) {
        MiningProgressState state = persistedProgress.get(blockKey);
        if (state == null) {
            return;
        }
        if (System.currentTimeMillis() - state.getUpdatedAtMs() > plugin.getConfigManager().miningResumeTimeoutMs) {
            persistedProgress.remove(blockKey);
        }
    }

    private boolean canPlayerTouchBlock(Player player, MiningTargetKey blockKey) {
        long now = System.currentTimeMillis();
        Map<MiningTargetKey, Long> touches = playerTouchedBlocks.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        touches.entrySet().removeIf(entry -> now - entry.getValue() > plugin.getConfigManager().miningResumeTimeoutMs);

        if (touches.containsKey(blockKey)) {
            return true;
        }

        int limit = getPlayerTouchLimit(player);
        return touches.size() < limit;
    }

    private void registerTouch(Player player, MiningTargetKey blockKey) {
        Map<MiningTargetKey, Long> touches = playerTouchedBlocks.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        touches.put(blockKey, System.currentTimeMillis());
    }

    private int getPlayerTouchLimit(Player player) {
        int limit = plugin.getConfigManager().miningDefaultTouchLimit;
        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) {
                continue;
            }

            String permission = permissionInfo.getPermission().toLowerCase();
            if (!permission.startsWith(TOUCH_PERMISSION_PREFIX)) {
                continue;
            }

            String valuePart = permission.substring(TOUCH_PERMISSION_PREFIX.length());
            try {
                int extra = Integer.parseInt(valuePart);
                if (extra > 0) {
                    limit += extra;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed permission nodes.
            }
        }
        return Math.max(plugin.getConfigManager().miningDefaultTouchLimit, limit);
    }
}