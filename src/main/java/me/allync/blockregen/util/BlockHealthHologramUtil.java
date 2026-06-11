package me.allync.blockregen.util;

import me.allync.blockregen.BlockRegen;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Hologram per-blok untuk mode health menggunakan FancyHolograms.
 * Hanya support FancyHolograms agar tidak ada armor stand yang stuck di world.
 */
public final class BlockHealthHologramUtil {

    private static final int BAR_LENGTH = 10;
    /** Delay auto-remove hologram setelah tidak ada hit (dalam ticks, default 60 = 3s) */
    private static final long AUTO_REMOVE_DELAY_TICKS = 60L;

    /** locationKey → removal task */
    private static final Map<String, BukkitTask> REMOVAL_TASKS = new HashMap<>();

    private BlockHealthHologramUtil() {}

    /**
     * Update atau buat hologram di atas blok dengan HP saat ini.
     *
     * @param blockLocation Lokasi blok
     * @param currentHp     HP saat ini
     * @param maxHp         HP maksimal
     * @param plugin        Referensi plugin
     */
    public static void update(Location blockLocation, double currentHp, double maxHp, BlockRegen plugin) {
        if (blockLocation == null || plugin == null) return;
        if (!plugin.getConfigManager().blockHealthHologramEnabled) return;
        if (!BlockRegen.fancyHologramsEnabled) return;

        World world = blockLocation.getWorld();
        if (world == null) return;

        String key = key(blockLocation);
        float progress = (float) ((maxHp - currentHp) / maxHp); // 0 = penuh, 1 = mati

        // Baris atas: ❤ 50/100
        String topText = ColorUtil.color(
                "&c❤ &f" + formatHp(currentHp) + " &7/ &f" + formatHp(maxHp));
        // Baris bawah: health bar
        String bottomText = HealthBarUtil.build(progress, BAR_LENGTH);

        Location fancyLoc = blockLocation.clone().add(0.5, 1.55, 0.5);
        FancyHologramsHelper.createOrUpdateHealthHologram(fancyLoc, topText, bottomText);
        scheduleRemoval(key, blockLocation, plugin);
    }

    /**
     * Hapus hologram secara langsung (dipanggil saat blok hancur / regen).
     */
    public static void remove(Location blockLocation) {
        if (blockLocation == null) return;
        if (!BlockRegen.fancyHologramsEnabled) return;

        try {
            FancyHologramsHelper.removeHealthHologram(blockLocation.clone().add(0.5, 1.55, 0.5));
        } catch (Throwable t) {
            // Ignore soft-depend load errors
        }

        String key = key(blockLocation);
        BukkitTask task = REMOVAL_TASKS.remove(key);
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
    }

    /** Hapus semua hologram (plugin disable). */
    public static void removeAll() {
        if (!BlockRegen.fancyHologramsEnabled) return;

        try {
            FancyHologramsHelper.removeAllHealthHolograms();
        } catch (Throwable t) {
            // Ignore soft-depend load errors
        }

        for (BukkitTask task : REMOVAL_TASKS.values()) {
            if (task != null) {
                try { task.cancel(); } catch (Exception ignored) {}
            }
        }
        REMOVAL_TASKS.clear();
    }

    private static void scheduleRemoval(String key, Location loc, BlockRegen plugin) {
        BukkitTask existing = REMOVAL_TASKS.remove(key);
        if (existing != null) {
            try { existing.cancel(); } catch (Exception ignored) {}
        }

        BukkitTask removalTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (BlockRegen.fancyHologramsEnabled) {
                try {
                    FancyHologramsHelper.removeHealthHologram(loc.clone().add(0.5, 1.55, 0.5));
                } catch (Throwable t) {
                    // Ignore soft-depend load errors
                }
            }
            REMOVAL_TASKS.remove(key);
        }, AUTO_REMOVE_DELAY_TICKS);

        REMOVAL_TASKS.put(key, removalTask);
    }

    private static String formatHp(double hp) {
        if (hp == Math.floor(hp)) return String.valueOf((int) hp);
        return String.format("%.1f", hp);
    }

    private static String key(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }
}
