package me.allync.blockregen.util;

import me.allync.blockregen.BlockRegen;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Hologram per-blok untuk mode health.
 * Hologram float di atas blok dan TERLIHAT oleh semua pemain di sekitar.
 * Auto-remove setelah beberapa detik tidak ada serangan.
 */
public final class BlockHealthHologramUtil {

    private static final int BAR_LENGTH = 10;
    /** Delay auto-remove hologram setelah tidak ada hit (dalam ticks, default 60 = 3s) */
    private static final long AUTO_REMOVE_DELAY_TICKS = 60L;

    /** locationKey → entry hologram */
    private static final Map<String, HologramEntry> BLOCK_HOLOGRAMS = new HashMap<>();

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
        World world = blockLocation.getWorld();
        if (world == null) return;

        String key = key(blockLocation);
        float progress = (float) ((maxHp - currentHp) / maxHp); // 0 = penuh, 1 = mati

        // Baris atas: ❤ 50/100
        String topText = ColorUtil.color(
                "&c❤ &f" + formatHp(currentHp) + " &7/ &f" + formatHp(maxHp));
        // Baris bawah: health bar
        String bottomText = HealthBarUtil.build(progress, BAR_LENGTH);

        Location topLoc = blockLocation.clone().add(0.5, 1.7, 0.5);
        Location botLoc = blockLocation.clone().add(0.5, 1.45, 0.5);

        HologramEntry entry = BLOCK_HOLOGRAMS.get(key);
        if (entry == null || !entry.isValid()) {
            if (entry != null) entry.removeStands();
            ArmorStand top = spawnLine(world, topLoc);
            ArmorStand bot = spawnLine(world, botLoc);
            entry = new HologramEntry(top, bot);
            BLOCK_HOLOGRAMS.put(key, entry);
        } else {
            entry.top.teleport(topLoc);
            entry.bot.teleport(botLoc);
        }

        entry.top.setCustomName(topText);
        entry.bot.setCustomName(bottomText);

        // Jadwalkan auto-remove (reset setiap update)
        scheduleRemoval(key, blockLocation, plugin);
    }

    /**
     * Hapus hologram secara langsung (dipanggil saat blok hancur / regen).
     */
    public static void remove(Location blockLocation) {
        if (blockLocation == null) return;
        String key = key(blockLocation);
        HologramEntry entry = BLOCK_HOLOGRAMS.remove(key);
        if (entry != null) {
            if (entry.removalTask != null) entry.removalTask.cancel();
            entry.removeStands();
        }
    }

    /** Hapus semua hologram (plugin disable). */
    public static void removeAll() {
        for (HologramEntry entry : BLOCK_HOLOGRAMS.values()) {
            if (entry.removalTask != null) try { entry.removalTask.cancel(); } catch (Exception ignored) {}
            entry.removeStands();
        }
        BLOCK_HOLOGRAMS.clear();
    }

    // ─────────────────────────────────────────────────────────────

    private static void scheduleRemoval(String key, Location loc, BlockRegen plugin) {
        HologramEntry entry = BLOCK_HOLOGRAMS.get(key);
        if (entry == null) return;
        if (entry.removalTask != null) {
            try { entry.removalTask.cancel(); } catch (Exception ignored) {}
        }
        entry.removalTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            HologramEntry e = BLOCK_HOLOGRAMS.remove(key);
            if (e != null) e.removeStands();
        }, AUTO_REMOVE_DELAY_TICKS);
    }

    private static ArmorStand spawnLine(World world, Location location) {
        ArmorStand as = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setSmall(true);
        as.setCustomNameVisible(true);
        as.setInvulnerable(true);
        as.setSilent(true);
        return as;
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

    private static final class HologramEntry {
        ArmorStand top;
        ArmorStand bot;
        BukkitTask removalTask;

        HologramEntry(ArmorStand top, ArmorStand bot) {
            this.top = top;
            this.bot = bot;
        }

        boolean isValid() {
            return top != null && bot != null && top.isValid() && bot.isValid();
        }

        void removeStands() {
            if (top != null && top.isValid()) top.remove();
            if (bot != null && bot.isValid()) bot.remove();
        }
    }
}
