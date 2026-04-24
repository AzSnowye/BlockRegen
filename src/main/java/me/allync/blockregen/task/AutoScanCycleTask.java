package me.allync.blockregen.task;

import me.allync.blockregen.BlockRegen;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Repeating task that triggers the Auto-Scan cycle at a configured interval.
 */
public class AutoScanCycleTask extends BukkitRunnable {

    private final BlockRegen plugin;

    public AutoScanCycleTask(BlockRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getAutoScanManager().runCycle();
    }
}
