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
import me.allync.blockregen.data.ToolRequirement;
import me.allync.blockregen.manager.MiningManager;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.logging.Logger;

public class BlockBreakListener implements Listener {

    private final BlockRegen plugin;
    private final Logger logger;
    private final MiningManager miningManager; // BARU

    public BlockBreakListener(BlockRegen plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.miningManager = plugin.getMiningManager(); // BARU: Dapatkan dari main class
    }

    // Helper debug
    private void debug(Player player, String blockIdentifier, String message) {
        miningManager.debug(player, blockIdentifier, message);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockIdentifier = miningManager.getBlockIdentifier(block); // Gunakan helper dari MiningManager
        Set<String> regionNames = plugin.getRegionManager().getRegionNamesAt(block.getLocation());

        debug(player, blockIdentifier, "BlockBreakEvent triggered by " + player.getName());

        if (player.getGameMode() == GameMode.CREATIVE) {
            debug(player, blockIdentifier, "Player is in CREATIVE mode. &cIgnoring event.");
            return;
        }

        // --- AUTO-SCAN: block breaking inactive (placeholder) points ---
        if (plugin.getAutoScanManager().isInactivePoint(block.getLocation())) {
            debug(player, blockIdentifier, "Block is an inactive auto-scan point (placeholder). &cCancelling break.");
            event.setCancelled(true);
            return;
        }
        // --- END AUTO-SCAN ---

        if (!plugin.getConfigManager().allWorldsEnabled && !plugin.getConfigManager().enabledWorlds.contains(player.getWorld().getName())) {
            debug(player, blockIdentifier, "World '" + player.getWorld().getName() + "' is not an enabled world. &cIgnoring event.");
            return;
        }

        if (BlockRegen.harvestFlowEnabled) {
            Material blockType = block.getType();
            if (blockType == Material.WHEAT || blockType == Material.CARROTS ||
                    blockType == Material.POTATOES || blockType == Material.BEETROOTS ||
                    blockType == Material.NETHER_WART || blockType == Material.COCOA) {
                debug(player, blockIdentifier, "Block is a crop and HarvestFlow is enabled. &cIgnoring event.");
                return;
            }
        }

        // --- DAPATKAN BLOCK DATA LEBIH AWAL ---
        BlockData data = plugin.getBlockManager().getBlockData(blockIdentifier, regionNames);

        // --- PENGECEKAN BARU: JIKA BLOK INI DIATUR OLEH LISTENER BARU, ABAIKAN EVENT INI ---
        if (data != null && data.hasCustomBreakDuration()) {
            if (block.hasMetadata("blockbreakevent-ignore") || block.hasMetadata("blockregen-task-break")) {
                debug(player, blockIdentifier, "Block has custom break duration but metadata allows break. Allowing BlockBreakEvent.");
            } else {
                debug(player, blockIdentifier, "Block has custom break duration. &cIgnoring BlockBreakEvent&7 (handled by BlockMiningListener).");
                event.setCancelled(true); // Batalkan event untuk mencegah vanilla break jika terjadi lag
                return;
            }
        }
        // --- AKHIR PENGECEKAN BARU ---

        if (plugin.getConfigManager().worldGuardEnabled && plugin.getWorldGuardPlugin() != null) {
            debug(player, blockIdentifier, "WorldGuard integration is enabled. Checking region flags...");
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();

            boolean canBreak = query.testBuild(BukkitAdapter.adapt(block.getLocation()), localPlayer, Flags.BLOCK_BREAK);

            if (!canBreak) { // Flag is DENY
                debug(player, blockIdentifier, "WorldGuard check &cfailed&7 (block-break: DENY). Checking for override...");
                if (plugin.getConfigManager().worldGuardBreakRegenInDenyRegions && plugin.getBlockManager().isRegenBlockInRegion(blockIdentifier, regionNames)) {
                    debug(player, blockIdentifier, "Override enabled and block is a regen block. &aForcing event to proceed.");
                    event.setCancelled(false); // Force the event to proceed
                } else {
                    debug(player, blockIdentifier, "Override disabled or not a regen block. &cEvent cancelled by WorldGuard.");
                    return; // Respect the DENY
                }
            } else { // Flag is ALLOW
                if (plugin.getConfigManager().worldGuardDisableOtherBreak) {
                    debug(player, blockIdentifier, "'worldGuardDisableOtherBreak' is enabled.");
                    if (!plugin.getBlockManager().isRegenBlockInRegion(blockIdentifier, regionNames)) {
                        if (plugin.isPlayerBypassing(player)) {
                            debug(player, blockIdentifier, "Player is in bypass mode. &aAllowing normal block break.");
                            return;
                        } else {
                            debug(player, blockIdentifier, "Block is not a regen block. &cCancelling event due to WorldGuard config.");
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
            debug(player, blockIdentifier, "WorldGuard check &apassed&7.");
        }

        if (event.isCancelled()) {
            debug(player, blockIdentifier, "Event is cancelled. Aborting.");
            return;
        }

        if (!plugin.getBlockManager().isRegenBlockInRegion(blockIdentifier, regionNames)) {
            debug(player, blockIdentifier, "Block '" + blockIdentifier + "' is not a configured regen block. &cIgnoring.");
            return;
        }

        debug(player, blockIdentifier, "Block is a regen block. &aContinuing checks...");

        if (plugin.getConfigManager().preventMiningWhenFull) {
            debug(player, blockIdentifier, "Checking if player inventory is full...");
            if (player.getInventory().firstEmpty() == -1) {
                // BlockData seharusnya sudah didapat di atas
                if (data != null && (data.isNaturalDrop() || data.hasCustomDrops())) {
                    debug(player, blockIdentifier, "Player inventory is full and the block has drops. &cCancelling event.");
                    player.sendMessage(plugin.getConfigManager().inventoryFullMessage);
                    event.setCancelled(true);
                    return;
                }
                debug(player, blockIdentifier, "Player inventory is full, but the block has no drops. &aAllowing break.");
            } else {
                debug(player, blockIdentifier, "Player inventory has space. &aContinuing...");
            }
        }

        boolean inSupportedRegion = plugin.getConfigManager().worldGuardEnabled
                ? plugin.getRegionManager().isLocationInAnySupportedRegion(block.getLocation())
                : plugin.getRegionManager().isLocationInRegion(block.getLocation());
        if (!inSupportedRegion) {
            debug(player, blockIdentifier, "Location is not inside a supported region (BlockRegen/WorldGuard). &cCancelling to prevent block loss.");
            // Cancel the event so the regen block is not permanently destroyed by vanilla.
            event.setCancelled(true);
            if (player.hasPermission("blockregen.admin")) {
                player.sendMessage(plugin.getConfigManager().notInRegionMessage);
            }
            return;
        }
        debug(player, blockIdentifier, "Location is within a supported region. &aContinuing...");
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            debug(player, blockIdentifier, "Block is already regenerating. &cCancelling event.");
            event.setCancelled(true);
            return;
        }

        // BlockData 'data' sudah didapat di atas
        if (data == null) {
            debug(player, blockIdentifier, "&cBlockData is null for " + blockIdentifier + ". This should not happen. Aborting.");
            return;
        }
        final BlockState originalState = block.getState();

        if (data.requiresTool()) {
            debug(player, blockIdentifier, "Tool requirement check initiated.");
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            boolean toolMatches = false;
            for (ToolRequirement requirement : data.getRequiredTools()) {
                if (requirement.matches(itemInHand)) {
                    toolMatches = true;
                    debug(player, blockIdentifier, "&aTool requirement met: &f" + requirement.toString());
                    break;
                }
            }
            if (!toolMatches) {
                debug(player, blockIdentifier, "&cTool requirement not met. Player's item: &f" + itemInHand.getType());
                String requiredTools = miningManager.formatRequiredTools(data);
                player.sendMessage(plugin.getConfigManager().wrongToolMessage.replace("%tool%", requiredTools));
                SoundUtil.playSoundToPlayer(player, block.getLocation(), plugin.getConfigManager().wrongToolSound, null);
                event.setCancelled(true);
                return;
            }
        }

        debug(player, blockIdentifier, "Handling drops, commands, and EXP via MiningManager...");

        // --- PANGGILAN LOGIKA BARU ---
        event.setDropItems(false); // Hentikan drop vanilla
        event.setExpToDrop(0); // Hentikan EXP vanilla

        // Panggil MiningManager untuk menangani sisanya
        miningManager.processBlockBreak(player, block, data, originalState, blockIdentifier);
        // --- AKHIR PANGGILAN LOGIKA BARU ---

        // Logika lama (drops, commands, exp, sound, particle, regen) dihapus
        // karena sudah dipindahkan ke MiningManager.processBlockBreak()
    }

    // --- SEMUA METODE HELPER (handleAllDrops, getBlockIdentifier, handleMMOCoreExp, dll) ---
    // --- TELAH DIPINDAHKAN KE MiningManager.java ---
}