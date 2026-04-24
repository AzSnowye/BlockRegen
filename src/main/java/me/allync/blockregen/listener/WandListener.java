package me.allync.blockregen.listener;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.manager.RegionManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class WandListener implements Listener {

    // Display name used to identify the scan wand item
    public static final String SCAN_WAND_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Auto-Scan Wand";

    private final BlockRegen plugin;
    private final RegionManager regionManager;

    public WandListener(BlockRegen plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process for the main hand to prevent double messages
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            return;
        }

        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        String displayName = meta.getDisplayName();

        // --- Check for Auto-Scan Wand ---
        if (displayName.equals(SCAN_WAND_NAME)) {
            handleScanWand(event, player);
            return;
        }

        // --- Check for Region Selection Wand ---
        if (!itemInHand.getType().toString().equals(plugin.getConfigManager().wandMaterial.toString())
                || !displayName.equals(plugin.getConfigManager().wandName)) {
            return;
        }

        if (!player.hasPermission("blockregen.admin")) {
            return;
        }

        event.setCancelled(true);

        Action action = event.getAction();
        Location location = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;
        if (location == null) return;

        if (action == Action.LEFT_CLICK_BLOCK) {
            regionManager.setPos1(player, location);
            player.sendMessage(plugin.getConfigManager().prefix + plugin.getConfigManager().pos1Message
                    .replace("%location%", formatLocation(location)));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            regionManager.setPos2(player, location);
            player.sendMessage(plugin.getConfigManager().prefix + plugin.getConfigManager().pos2Message
                    .replace("%location%", formatLocation(location)));
        }
    }

    private void handleScanWand(PlayerInteractEvent event, Player player) {
        if (!player.hasPermission("blockregen.admin")) {
            return;
        }

        event.setCancelled(true);

        Location location = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;
        if (location == null) {
            player.sendMessage(plugin.getConfigManager().prefix + "§cArahkan ke sebuah block terlebih dahulu.");
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        int result = plugin.getAutoScanManager().toggle(location);
        String loc = formatLocation(location);

        if (result == 1) {
            player.sendMessage(plugin.getConfigManager().prefix
                    + "§aBlock di §e" + loc + " §atelah didaftarkan sebagai Auto-Scan point.");
        } else if (result == -1) {
            player.sendMessage(plugin.getConfigManager().prefix
                    + "§cBlock di §e" + loc + " §ctelah dihapus dari daftar Auto-Scan.");
        } else {
            player.sendMessage(plugin.getConfigManager().prefix
                    + "§cBlock ini bukan regen block yang terdaftar di blocks.yml.");
        }
    }

    /**
     * Creates the Auto-Scan Wand item stack. Used by the command handler.
     */
    public static ItemStack createScanWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(SCAN_WAND_NAME);
            meta.setLore(Arrays.asList(
                    "§7Klik kanan/kiri block untuk",
                    "§7mendaftar/menghapus Auto-Scan point."
            ));
            wand.setItemMeta(meta);
        }
        return wand;
    }

    private String formatLocation(Location loc) {
        return String.format("X: %d, Y: %d, Z: %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
