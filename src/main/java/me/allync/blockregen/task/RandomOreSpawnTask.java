package me.allync.blockregen.task;

import me.allync.blockregen.BlockRegen;
import org.bukkit.scheduler.BukkitRunnable;

public class RandomOreSpawnTask extends BukkitRunnable {

    private final BlockRegen plugin;

    public RandomOreSpawnTask(BlockRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getRandomOreManager().isEnabled()) {
            return;
        }
        plugin.getRandomOreManager().randomizeNow();
    }
}

