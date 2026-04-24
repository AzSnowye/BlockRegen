package me.allync.blockregen.manager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

    public RegenManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public void startRegen(BlockState originalState, int delay, String blockIdentifier, String regenVariantIdentifier) {
        this.regeneratingBlocks.put(originalState.getLocation(), originalState);
        (new RegenTask(this.plugin, this, originalState, blockIdentifier, regenVariantIdentifier)).runTaskLater((Plugin)this.plugin, delay * 20L);
    }

    public void startRelocationCooldown(BlockState stateDuringCooldown, int delaySeconds, Runnable onFinish) {
        if (stateDuringCooldown == null || stateDuringCooldown.getLocation() == null) {
            return;
        }

        Location location = stateDuringCooldown.getLocation();
        this.regeneratingBlocks.put(location, stateDuringCooldown);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removeRegenerating(location);
            if (onFinish != null) {
                onFinish.run();
            }
        }, Math.max(1L, delaySeconds * 20L));
    }

    public boolean isRegenerating(Location location) {
        return this.regeneratingBlocks.containsKey(location);
    }

    public void removeRegenerating(Location location) {
        this.regeneratingBlocks.remove(location);
    }

    public void handleShutdown() {
        this.plugin.getLogger().info("Server is shutting down. Regenerating all pending blocks immediately...");
        (new HashMap<>(this.regeneratingBlocks)).forEach((location, state) -> state.update(true, false));
        this.regeneratingBlocks.clear();
        this.plugin.getLogger().info("All pending blocks have been regenerated.");
    }

    /**
     * Returns how many blocks are currently waiting to regenerate.
     */
    public int getPendingCount() {
        return regeneratingBlocks.size();
    }

    /**
     * Returns an unmodifiable view of all locations currently pending regen.
     */
    public Set<Location> getPendingLocations() {
        return Collections.unmodifiableSet(regeneratingBlocks.keySet());
    }

    /**
     * Force-regenerates ALL pending blocks immediately (cancels delay).
     * @return number of blocks that were force-regenerated.
     */
    public int forceRegenAll() {
        if (regeneratingBlocks.isEmpty()) return 0;
        Map<Location, BlockState> copy = new HashMap<>(regeneratingBlocks);
        regeneratingBlocks.clear();
        copy.forEach((location, state) -> state.update(true, false));
        return copy.size();
    }

    /**
     * Force-regenerates a single block at the given location.
     * @return true if a pending block was found and regenerated.
     */
    public boolean forceRegenLocation(Location location) {
        BlockState state = regeneratingBlocks.remove(location);
        if (state == null) return false;
        state.update(true, false);
        return true;
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