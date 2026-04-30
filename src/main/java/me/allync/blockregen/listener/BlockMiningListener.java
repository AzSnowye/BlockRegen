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
import me.allync.blockregen.task.PlayerHealthMiningTask;
import me.allync.blockregen.task.PlayerMiningTask;
import me.allync.blockregen.util.ItemUtil;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listener untuk sistem penambangan kustom dengan durasi.
 *
 * Mekanisme: pemain klik KANAN (RIGHT_CLICK_BLOCK) untuk memulai & melanjutkan penambangan.
 * Setiap klik kanan = satu "hit". Jika berhenti klik > hold-timeout-ms, mining dibatalkan.
 *
 * Fitur Stackable: Jika ada blok berdampingan dengan konfigurasi sama, semua ikut dibreak.
 */
public class BlockMiningListener implements Listener {

    private static final String TOUCH_PERMISSION_PREFIX = "blockregen.multiplier.block.";

    private final BlockRegen plugin;
    private final MiningManager miningManager;

    private final Map<UUID, PlayerMiningTask> activeMiningTasks = new HashMap<>();
    private final Map<UUID, PlayerHealthMiningTask> activeHealthTasks = new HashMap<>();
    private final Map<MiningTargetKey, UUID> activeBlockMiners = new HashMap<>();
    private final Map<UUID, Long> mineConflictMessageCooldown = new HashMap<>();
    private final Map<MiningTargetKey, MiningProgressState> persistedProgress = new HashMap<>();
    private final Map<UUID, Map<MiningTargetKey, Long>> playerTouchedBlocks = new HashMap<>();

    public BlockMiningListener(BlockRegen plugin) {
        this.plugin = plugin;
        this.miningManager = plugin.getMiningManager();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN EVENT: RIGHT-CLICK BLOCK
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Hanya proses klik KANAN pada blok
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        // --- 1. Initial Checks ---
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!plugin.getConfigManager().allWorldsEnabled
                && !plugin.getConfigManager().enabledWorlds.contains(player.getWorld().getName())) return;

        if (BlockRegen.harvestFlowEnabled) {
            Material blockType = block.getType();
            if (blockType == Material.WHEAT || blockType == Material.CARROTS
                    || blockType == Material.POTATOES || blockType == Material.BEETROOTS
                    || blockType == Material.NETHER_WART || blockType == Material.COCOA) {
                return;
            }
        }

        String blockIdentifier = miningManager.getBlockIdentifier(block);
        Set<String> regionNames = plugin.getRegionManager().getRegionNamesAt(block.getLocation());

        // --- 2. Get BlockData & dispatch by mode ---
        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier, regionNames);
        if (data == null) return; // Bukan regen block

        // Mode health-based: setiap klik kanan mengurangi HP
        if (data.hasBlockHealth()) {
            event.setCancelled(true);
            handleHealthBlock(player, block, data, blockIdentifier, regionNames);
            return;
        }

        if (!data.hasCustomBreakDuration()) {
            // BlockBreakListener yang menangani (vanilla break / instant break)
            return;
        }

        // Cegah interaction bawaan (misal: membuka chest, dll) saat mining
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        MiningTargetKey blockKey = MiningTargetKey.from(block.getLocation());

        cleanupExpiredProgress(blockKey);

        // Jika player sudah mining blok tertentu —
        PlayerMiningTask existingTask = activeMiningTasks.get(uuid);
        if (existingTask != null) {
            if (!existingTask.getBlockLocation().equals(block.getLocation())) {
                // Klik blok berbeda → batalkan yang lama
                miningManager.debug(player, blockIdentifier, "&cSwitching target block. Cancelling old task.");
                existingTask.cancelTask();
                // lanjut untuk mulai task baru
            } else {
                // Klik blok yang sama → daftarkan hit supaya timer tidak timeout
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
                activeBlockMiners.remove(blockKey); // stale lock
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

            if (!canBreak) {
                if (!plugin.getConfigManager().worldGuardBreakRegenInDenyRegions) {
                    miningManager.debug(player, blockIdentifier, "WorldGuard check &cfailed&7 (block-break: DENY).");
                    return;
                }
            } else {
                if (plugin.getConfigManager().worldGuardDisableOtherBreak) {
                    if (!plugin.getBlockManager().isRegenBlockInRegion(blockIdentifier, regionNames)) {
                        if (!plugin.isPlayerBypassing(player)) {
                            miningManager.debug(player, blockIdentifier, "Block is not a regen block. &cCancelling due to WG config.");
                            return;
                        }
                    }
                }
            }
        }

        // --- 4. Region / Regenerating checks ---
        boolean inSupportedRegion = plugin.getConfigManager().worldGuardEnabled
                ? plugin.getRegionManager().isLocationInAnySupportedRegion(block.getLocation())
                : plugin.getRegionManager().isLocationInRegion(block.getLocation());
        if (!inSupportedRegion) {
            miningManager.debug(player, blockIdentifier, "Location is not inside a supported region. &cIgnoring.");
            return;
        }

        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            miningManager.debug(player, blockIdentifier, "Block is already regenerating. &cCancelling.");
            return;
        }

        // --- 5. Pickaxe Power & Tool Checks ---
        double power = (data.requiresPickaxePower() || data.requiresTool())
                ? ItemUtil.getPickaxePower(player.getInventory().getItemInMainHand())
                : 0.0;

        if (data.requiresPickaxePower() || data.requiresTool()) {
            miningManager.debug(player, blockIdentifier, "&7Pickaxe power terdeteksi: &f" + power
                    + (data.requiresPickaxePower() ? " &7(req: &f" + data.getRequirePickaxePower() + "&7)" : ""));
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
            if (!toolMatches && data.requiresPickaxePower() && power >= data.getRequirePickaxePower()) {
                toolMatches = true;
                miningManager.debug(player, blockIdentifier, "&aTool requirement met via pickaxe power (" + power + " >= " + data.getRequirePickaxePower() + ")");
            }
            if (!toolMatches) {
                miningManager.debug(player, blockIdentifier, "&cTool requirement not met.");
                String requiredTools = miningManager.formatRequiredTools(data);
                player.sendMessage(plugin.getConfigManager().wrongToolMessage.replace("%tool%", requiredTools));
                SoundUtil.playSoundToPlayer(player, block.getLocation(), plugin.getConfigManager().wrongToolSound, null);
                return;
            }
        }

        if (data.requiresPickaxePower()) {
            if (power < data.getRequirePickaxePower()) {
                miningManager.debug(player, blockIdentifier, "&cPickaxe power too low (" + power + " < " + data.getRequirePickaxePower() + ")");
                String reqStr = String.valueOf((int) data.getRequirePickaxePower());
                String curStr = String.format("%.1f", power);
                String msg = plugin.getConfigManager().lowPickaxePowerMessage
                        .replace("%power%", reqStr)
                        .replace("%your_power%", curStr);
                player.sendMessage(msg);
                SoundUtil.playSoundToPlayer(player, block.getLocation(), plugin.getConfigManager().wrongToolSound, null);
                return;
            }
            miningManager.debug(player, blockIdentifier, "&aPickaxe power requirement met (" + power + " >= " + data.getRequirePickaxePower() + ")");
        }

        // --- 6. Cari blok stackable berdampingan ---
        List<Block> stackedBlocks;
        if (plugin.getConfigManager().stackableBlocksEnabled) {
            stackedBlocks = findStackedBlocks(block, blockIdentifier, regionNames);
            miningManager.debug(player, blockIdentifier, "&7Stack: " + stackedBlocks.size() + " blok terdeteksi (termasuk blok utama).");
        } else {
            stackedBlocks = new java.util.ArrayList<>();
            stackedBlocks.add(block);
        }

        // --- 7. Start Task ---
        miningManager.debug(player, blockIdentifier, "Started mining. Duration: " + data.getBreakDuration() + "s");
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
                resumedElapsedMs,
                null,
                stackedBlocks    // ← daftar blok stackable
        );
        newTask.runTaskTimer(plugin, 0L, 1L);
        activeMiningTasks.put(uuid, newTask);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────
    // HEALTH BLOCK LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Proses hit pada blok mode health.
     * Setiap klik kanan mengurangi HP blok sebesar pickaxe power pemain.
     */
    private void handleHealthBlock(Player player, Block block, BlockData data,
                                   String blockIdentifier, Set<String> regionNames) {
        UUID uuid = player.getUniqueId();

        // Anti auto-clicker: jika pemain sudah punya task health yang berjalan untuk blok ini, abaikan klik ini.
        PlayerHealthMiningTask existingTask = activeHealthTasks.get(uuid);
        if (existingTask != null) {
            if (existingTask.getBlock().getLocation().equals(block.getLocation())) {
                return; // Sedang menambang blok ini, abaikan klik tambahan
            } else {
                existingTask.cancelTask(); // Ganti blok, batalkan task lama
            }
        }

        // Mulai task baru yang akan berjalan otomatis per detik
        PlayerHealthMiningTask newTask = new PlayerHealthMiningTask(
                plugin, player, block, data, blockIdentifier, activeHealthTasks
        );
        newTask.runTaskTimer(plugin, 0L, 1L); // Cek target tiap tick, damage tiap 20 tick (1s)
        activeHealthTasks.put(uuid, newTask);

        miningManager.debug(player, blockIdentifier, "&a[Health] Auto-mining started. Keep looking at the block!");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STACKABLE BLOCK LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mencari blok-blok berdampingan (6 arah face) yang memiliki blockIdentifier sama
     * dan merupakan regen block yang valid. Menggunakan BFS agar bisa menemukan cluster.
     *
     * @param origin          Blok asal
     * @param blockIdentifier ID blok yang sedang ditambang
     * @param regionNames     Region di lokasi asal
     * @return Daftar blok (termasuk origin), maksimal MAX_STACK_SIZE buah
     */
    private List<Block> findStackedBlocks(Block origin, String blockIdentifier, Set<String> regionNames) {
        int maxSize = plugin.getConfigManager().stackableBlocksMaxSize;
        List<Block> result = new ArrayList<>();
        result.add(origin);

        // BFS — cari blok tetangga dengan tipe sama
        List<Block> queue = new ArrayList<>();
        queue.add(origin);
        Set<String> visited = new java.util.HashSet<>();
        visited.add(locationKey(origin));

        BlockFace[] faces = {
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST,  BlockFace.WEST,
            BlockFace.UP,    BlockFace.DOWN
        };

        while (!queue.isEmpty() && result.size() < maxSize) {
            Block current = queue.remove(0);
            for (BlockFace face : faces) {
                if (result.size() >= maxSize) break;
                Block neighbor = current.getRelative(face);
                String key = locationKey(neighbor);
                if (visited.contains(key)) continue;
                visited.add(key);

                String neighborId = miningManager.getBlockIdentifier(neighbor);
                if (!neighborId.equalsIgnoreCase(blockIdentifier)) continue;

                // Pastikan blok tetangga juga valid sebagai regen block
                Set<String> neighborRegions = plugin.getRegionManager().getRegionNamesAt(neighbor.getLocation());
                BlockData neighborData = plugin.getBlockManager().getBlockData(neighborId, neighborRegions);
                if (neighborData == null) continue;
                if (plugin.getRegenManager().isRegenerating(neighbor.getLocation())) continue;
                if (miningManager.isBeingMined(neighbor.getLocation())) continue;

                result.add(neighbor);
                queue.add(neighbor);
            }
        }

        return result;
    }

    private static String locationKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLAYER LIFECYCLE EVENTS
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelTaskFor(uuid);
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

    @EventHandler
    public void onPlayerItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        cancelTaskFor(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        cancelTaskFor(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void maybeSendMineConflictMessage(Player player) {
        long now = System.currentTimeMillis();
        long lastSent = mineConflictMessageCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastSent < 750L) return;
        mineConflictMessageCooldown.put(player.getUniqueId(), now);
        player.sendMessage(plugin.getConfigManager().blockBeingMinedMessage);
    }

    public void shutdown() {
        for (PlayerMiningTask task : activeMiningTasks.values()) {
            if (task != null) task.cancelTask();
        }
        for (PlayerHealthMiningTask task : activeHealthTasks.values()) {
            if (task != null) task.cancelTask();
        }
        activeMiningTasks.clear();
        activeHealthTasks.clear();
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
        PlayerHealthMiningTask existingHealthTask = activeHealthTasks.get(uuid);
        if (existingHealthTask != null) {
            existingHealthTask.cancelTask();
        }
        mineConflictMessageCooldown.remove(uuid);
        playerTouchedBlocks.remove(uuid);
    }

    private void cleanupExpiredProgress(MiningTargetKey blockKey) {
        MiningProgressState state = persistedProgress.get(blockKey);
        if (state == null) return;
        if (System.currentTimeMillis() - state.getUpdatedAtMs() > plugin.getConfigManager().miningResumeTimeoutMs) {
            persistedProgress.remove(blockKey);
        }
    }

    private boolean canPlayerTouchBlock(Player player, MiningTargetKey blockKey) {
        long now = System.currentTimeMillis();
        Map<MiningTargetKey, Long> touches = playerTouchedBlocks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        touches.entrySet().removeIf(entry -> now - entry.getValue() > plugin.getConfigManager().miningResumeTimeoutMs);
        if (touches.containsKey(blockKey)) return true;
        int limit = getPlayerTouchLimit(player);
        return touches.size() < limit;
    }

    private void registerTouch(Player player, MiningTargetKey blockKey) {
        Map<MiningTargetKey, Long> touches = playerTouchedBlocks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        touches.put(blockKey, System.currentTimeMillis());
    }

    private int getPlayerTouchLimit(Player player) {
        int limit = plugin.getConfigManager().miningDefaultTouchLimit;
        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) continue;
            String permission = permissionInfo.getPermission().toLowerCase();
            if (!permission.startsWith(TOUCH_PERMISSION_PREFIX)) continue;
            String valuePart = permission.substring(TOUCH_PERMISSION_PREFIX.length());
            try {
                int extra = Integer.parseInt(valuePart);
                if (extra > 0) limit += extra;
            } catch (NumberFormatException ignored) {}
        }
        return Math.max(plugin.getConfigManager().miningDefaultTouchLimit, limit);
    }

    public boolean isBlockBusy(org.bukkit.Location loc) {
        MiningTargetKey key = MiningTargetKey.from(loc);
        if (activeBlockMiners.containsKey(key)) return true;
        if (persistedProgress.containsKey(key)) {
            MiningProgressState state = persistedProgress.get(key);
            if (System.currentTimeMillis() - state.getUpdatedAtMs() < plugin.getConfigManager().miningResumeTimeoutMs) {
                return true;
            }
        }
        for (PlayerHealthMiningTask task : activeHealthTasks.values()) {
            if (task.getBlock().getLocation().equals(loc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Batalkan task mining di lokasi ini secara langsung.
     */
    public void cancelTaskAt(org.bukkit.Location loc) {
        if (loc == null) return;
        MiningTargetKey key = MiningTargetKey.from(loc);
        UUID miner = activeBlockMiners.get(key);
        if (miner != null) {
            PlayerMiningTask task = activeMiningTasks.get(miner);
            if (task != null) task.cancelTask();
        }
        for (PlayerHealthMiningTask task : activeHealthTasks.values()) {
            if (task.getBlock().getLocation().equals(loc)) {
                task.cancelTask();
                break;
            }
        }
        persistedProgress.remove(key);
        activeBlockMiners.remove(key);
    }

    public void cleanupPlayer(UUID uuid) {
        playerTouchedBlocks.remove(uuid);
        mineConflictMessageCooldown.remove(uuid);
    }
}