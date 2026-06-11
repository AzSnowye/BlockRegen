package me.allync.blockregen.listener;

import me.allync.blockregen.BlockRegen;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Collection;

public class BlockPlaceListener implements Listener {

    private final BlockRegen plugin;

    public BlockPlaceListener(BlockRegen plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Collection<String> regionNames = plugin.getRegionManager().getRegionNamesAt(block.getLocation());
        String identifier = plugin.getMiningManager().getBlockIdentifier(block, regionNames);

        if (identifier != null && plugin.getBlockManager().isRegenBlockInRegion(identifier, regionNames)) {
            plugin.getIdleRotationManager().schedule(block.getLocation(), identifier);
        }
    }
}
