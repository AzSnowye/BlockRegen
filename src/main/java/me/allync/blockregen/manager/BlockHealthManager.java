package me.allync.blockregen.manager;

import me.allync.blockregen.BlockRegen;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Melacak HP saat ini per lokasi blok untuk mode "block-health".
 *
 * HP bersifat shared: semua pemain yang menyerang blok yang sama
 * mempengaruhi HP yang sama.
 * HP di-reset setelah timeout tidak ada serangan.
 */
public class BlockHealthManager {

    private final BlockRegen plugin;
    /** Lokasi blok → HP saat ini */
    private final Map<String, Double> currentHealth = new HashMap<>();
    /** Lokasi blok → timestamp terakhir serangan (ms) */
    private final Map<String, Long> lastHitTime = new HashMap<>();

    public BlockHealthManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    /**
     * Mengurangi HP blok sejumlah damage.
     *
     * @param loc       Lokasi blok
     * @param maxHp     HP maksimal blok (dari BlockData)
     * @param damage    Jumlah damage (= pickaxe power pemain)
     * @return HP blok setelah dikurangi (minimal 0)
     */
    public double damage(Location loc, double maxHp, double damage) {
        String key = key(loc);
        // Cek apakah HP sudah expired (reset ke max)
        maybeReset(key, maxHp);
        double current = currentHealth.getOrDefault(key, maxHp);
        double newHp = Math.max(0.0, current - damage);
        currentHealth.put(key, newHp);
        lastHitTime.put(key, System.currentTimeMillis());
        return newHp;
    }

    /**
     * Dapatkan HP saat ini tanpa mengubahnya.
     */
    public double getHp(Location loc, double maxHp) {
        String key = key(loc);
        maybeReset(key, maxHp);
        return currentHealth.getOrDefault(key, maxHp);
    }

    /**
     * Reset HP blok ke maksimal (dipanggil saat blok selesai dibreak atau regen).
     */
    public void reset(Location loc) {
        String key = key(loc);
        currentHealth.remove(key);
        lastHitTime.remove(key);
    }

    /** Hapus semua data (dipanggil saat plugin disable). */
    public void clear() {
        currentHealth.clear();
        lastHitTime.clear();
    }

    // ─────────────────────────────────────────────────────────────
    private void maybeReset(String key, double maxHp) {
        Long lastHit = lastHitTime.get(key);
        if (lastHit == null) return;
        long timeoutMs = plugin.getConfigManager().blockHealthResetTimeoutMs;
        if (System.currentTimeMillis() - lastHit > timeoutMs) {
            currentHealth.remove(key);
            lastHitTime.remove(key);
        }
    }

    private static String key(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getBlockX()
                + ":" + loc.getBlockY()
                + ":" + loc.getBlockZ();
    }
}
