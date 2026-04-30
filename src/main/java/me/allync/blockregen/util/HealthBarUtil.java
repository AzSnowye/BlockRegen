package me.allync.blockregen.util;

/**
 * Utility untuk menghasilkan health bar visual (progress bar) untuk hologram mining.
 * Menampilkan bar yang mengecil seiring block dirusak — seperti HP batu.
 */
public final class HealthBarUtil {

    private HealthBarUtil() {}

    /**
     * Menghasilkan health bar berbentuk karakter Unicode.
     * Bar yang tersisa berwarna hijau/kuning/merah tergantung persentase.
     *
     * @param progress  Nilai 0.0 (mulai) sampai 1.0 (selesai hancur)
     * @param barLength Jumlah karakter total bar
     * @return String bar berwarna siap ditampilkan di hologram
     */
    public static String build(float progress, int barLength) {
        // progress = 0.0 → batu penuh, 1.0 → batu hancur
        float remaining = 1.0f - Math.max(0f, Math.min(1f, progress));
        int filled = Math.round(remaining * barLength);
        int empty = barLength - filled;

        String fillColor = getColor(remaining);
        String emptyColor = "&8"; // abu gelap untuk slot kosong

        StringBuilder sb = new StringBuilder();
        sb.append(fillColor);
        for (int i = 0; i < filled; i++) {
            sb.append("█");
        }
        sb.append(emptyColor);
        for (int i = 0; i < empty; i++) {
            sb.append("█");
        }

        return ColorUtil.color(sb.toString());
    }

    /**
     * Versi dengan label persen di sebelah kanan.
     *
     * @param progress  Nilai 0.0–1.0
     * @param barLength Panjang bar
     * @return Bar dengan label persentase
     */
    public static String buildWithPercent(float progress, int barLength) {
        float remaining = 1.0f - Math.max(0f, Math.min(1f, progress));
        int pct = (int) (remaining * 100);
        return build(progress, barLength) + ColorUtil.color(" &f" + pct + "%");
    }

    /**
     * Menentukan warna bar berdasarkan persentase HP yang tersisa.
     */
    private static String getColor(float remaining) {
        if (remaining > 0.6f) return "&a";      // hijau
        if (remaining > 0.3f) return "&e";      // kuning
        return "&c";                              // merah
    }
}
