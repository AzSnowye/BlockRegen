package me.allync.blockregen.api;

import me.allync.blockregen.BlockRegen;
import org.bukkit.Location;
import org.bukkit.block.Block;
import java.util.Set;

public class BlockRegenAPIImpl implements BlockRegenAPI {

    private final BlockRegen plugin;

    public BlockRegenAPIImpl(BlockRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isRegenerating(Location location) {
        return plugin.getRegenManager().isRegenerating(location);
    }

    @Override
    public void forceRegen(Location location) {
        plugin.getRegenManager().forceRegen(location);
    }

    @Override
    public long getRemainingRegenTime(Location location) {
        return plugin.getRegenManager().getRemainingRegenTime(location);
    }

    @Override
    public boolean isInRegenRegion(Location location) {
        return plugin.getRegionManager().isLocationInRegion(location);
    }

    @Override
    public Set<String> getRegionsAt(Location location) {
        return plugin.getRegionManager().getRegionNamesAt(location);
    }

    @Override
    public String getBlockConfigIdentifier(Block block) {
        Set<String> regions = getRegionsAt(block.getLocation());
        return plugin.getMiningManager().getBlockIdentifier(block, regions);
    }

    @Override
    public String getBlockConfigIdentifier(Block block, Set<String> regionNames) {
        return plugin.getMiningManager().getBlockIdentifier(block, regionNames);
    }

    @Override
    public boolean isConfiguredBlock(Block block) {
        return getBlockConfigIdentifier(block) != null;
    }

    @Override
    public boolean isConfiguredBlock(Block block, Set<String> regionNames) {
        return getBlockConfigIdentifier(block, regionNames) != null;
    }
}
