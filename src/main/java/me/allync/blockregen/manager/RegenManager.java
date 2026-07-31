package me.allync.blockregen.manager;

import java.util.HashMap;
import java.util.Map;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.task.RegenTask;
import me.allync.blockregen.util.DurationFormatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class RegenManager {
    private final BlockRegen plugin;

    private final Map<Location, BlockState> regeneratingBlocks = new HashMap<>();
    private final Map<Location, org.bukkit.scheduler.BukkitTask> regenTasks = new HashMap<>();
    private final Map<Location, RegenTask> regenRunnables = new HashMap<>();
    private final Map<Location, Long> regenEndTimes = new HashMap<>();

    public RegenManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public void startRegen(BlockState originalState, int delay, String blockIdentifier, String regenVariantIdentifier) {
        Location loc = originalState.getLocation();
        this.regeneratingBlocks.put(loc, originalState);
        RegenTask runnable = new RegenTask(this.plugin, this, originalState, blockIdentifier, regenVariantIdentifier);
        org.bukkit.scheduler.BukkitTask task = runnable.runTaskLater((Plugin)this.plugin, delay * 20L);
        this.regenTasks.put(loc, task);
        this.regenRunnables.put(loc, runnable);
        this.regenEndTimes.put(loc, System.currentTimeMillis() + (delay * 1000L));
    }

    public void startRelocationCooldown(BlockState stateDuringCooldown, int delaySeconds, Runnable onFinish) {
        if (stateDuringCooldown == null || stateDuringCooldown.getLocation() == null) {
            return;
        }

        Location location = stateDuringCooldown.getLocation();
        this.regeneratingBlocks.put(location, stateDuringCooldown);
        this.regenEndTimes.put(location, System.currentTimeMillis() + (delaySeconds * 1000L));
        org.bukkit.scheduler.BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removeRegenerating(location);
            if (onFinish != null) {
                onFinish.run();
            }
        }, Math.max(1L, delaySeconds * 20L));
        this.regenTasks.put(location, task);
    }

    public boolean isRegenerating(Location location) {
        return this.regeneratingBlocks.containsKey(location);
    }

    public void removeRegenerating(Location location) {
        this.regeneratingBlocks.remove(location);
        this.regenTasks.remove(location);
        this.regenRunnables.remove(location);
        this.regenEndTimes.remove(location);
    }

    public void cancelRegen(Location location) {
        org.bukkit.scheduler.BukkitTask task = this.regenTasks.remove(location);
        if (task != null) {
            task.cancel();
        }
        this.regeneratingBlocks.remove(location);
        this.regenRunnables.remove(location);
        this.regenEndTimes.remove(location);
    }

    public void forceRegen(Location location) {
        RegenTask runnable = this.regenRunnables.remove(location);
        org.bukkit.scheduler.BukkitTask task = this.regenTasks.remove(location);
        if (task != null) {
            task.cancel();
        }
        this.regenEndTimes.remove(location);
        BlockState state = this.regeneratingBlocks.remove(location);
        if (runnable != null) {
            runnable.run();
        } else if (state != null) {
            state.update(true, false);
        }
    }

    public long getRemainingRegenTime(Location location) {
        Long endTime = this.regenEndTimes.get(location);
        if (endTime == null) {
            return -1;
        }
        long diff = endTime - System.currentTimeMillis();
        return Math.max(0L, diff);
    }

    public void handleShutdown() {
        this.plugin.getLogger().info("Server is shutting down. Regenerating all pending blocks immediately...");
        (new HashMap<>(this.regeneratingBlocks)).forEach((location, state) -> state.update(true, false));
        this.regeneratingBlocks.clear();
        this.regenTasks.clear();
        this.regenRunnables.clear();
        this.regenEndTimes.clear();
        this.plugin.getLogger().info("All pending blocks have been regenerated.");
    }

    /**
     * Sends the action bar countdown message.
     * Dipindahkan ke sini agar bisa diakses oleh MiningManager.
     */
    public void sendActionBarMessage(Player player, int delay) {
        if (plugin.getConfigManager().sendRegenCountdown) {
            String formattedTime = DurationFormatUtil.formatDurationSeconds(delay);
            String message = plugin.getConfigManager().regenCountdownMessage
                    .replace("%time%s", formattedTime)
                    .replace("%time%", formattedTime);
            Component actionbar = LegacyComponentSerializer.legacySection().deserialize(message);
            player.sendActionBar(actionbar);
        }
    }
}