package me.allync.blockregen.listener;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import net.advancedplugins.ae.api.EnchantActivateEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Set;

public class AdvancedEnchantmentListener implements Listener {

    private final BlockRegen plugin;

    public AdvancedEnchantmentListener(BlockRegen plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnchantActivate(EnchantActivateEvent event) {
        if (!(event.getFirstEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getFirstEntity();
        // Since AE doesn't pass the block in the event, we approximate by checking the block the player is looking at.
        // It's mostly accurate for mining enchantments like Trench.
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return;
        }

        Set<String> regionNames = plugin.getRegionManager().getRegionNamesAt(block.getLocation());
        String identifier = plugin.getMiningManager().getBlockIdentifier(block, regionNames);

        if (plugin.getBlockManager().isRegenBlockInRegion(identifier, regionNames)) {
            BlockData data = plugin.getBlockManager().getBlockData(identifier, regionNames);
            if (data != null && !data.isAllowAdvancedEnchantments()) {
                event.setCancelled(true);
            }
        }
    }
}
