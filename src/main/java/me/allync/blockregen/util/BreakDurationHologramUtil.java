package me.allync.blockregen.util;

import me.allync.blockregen.BlockRegen;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles temporary per-player mining holograms.
 *
 * Menampilkan health bar (bar HP batu) di atas blok saat ditambang.
 * Bar akan mengecil seiring progress penambangan, dengan warna berubah:
 *   Hijau → Kuning → Merah (semakin mendekati hancur).
 */
public final class BreakDurationHologramUtil {

    /** Panjang bar dalam karakter. */
    private static final int BAR_LENGTH = 10;

    private static final Map<UUID, HologramPair> ACTIVE_HOLOGRAMS = new HashMap<>();

    private BreakDurationHologramUtil() {}

    /**
     * Update hologram untuk player tertentu.
     *
     * @param player        Player yang sedang menambang
     * @param blockLocation Lokasi blok yang ditambang
     * @param progress      0.0 = baru mulai, 1.0 = sudah selesai hancur
     * @param plugin        Referensi plugin
     */
    public static void update(Player player, Location blockLocation, float progress, BlockRegen plugin) {
        if (player == null || !player.isOnline() || blockLocation == null || plugin == null) {
            return;
        }
        if (!plugin.getConfigManager().breakDurationHologramEnabled) {
            remove(player);
            return;
        }

        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }

        Location hologramLocation = blockLocation.clone().add(
                plugin.getConfigManager().breakDurationHologramOffsetX,
                plugin.getConfigManager().breakDurationHologramOffsetY,
                plugin.getConfigManager().breakDurationHologramOffsetZ);

        // Baris atas: label "Mining..."
        String topText = ChatColor.translateAlternateColorCodes('&', "&e⛏ Menambang...");
        // Baris bawah: health bar batu
        String bottomText = HealthBarUtil.buildWithPercent(progress, BAR_LENGTH);

        HologramPair hologramPair = ACTIVE_HOLOGRAMS.get(player.getUniqueId());

        if (hologramPair == null || !hologramPair.isValid()) {
            if (hologramPair != null) {
                hologramPair.remove();
            }
            ArmorStand topLine = createLine(world, hologramLocation.clone().add(0.0D, 0.3D, 0.0D));
            ArmorStand bottomLine = createLine(world, hologramLocation);
            hologramPair = new HologramPair(topLine, bottomLine);
            ACTIVE_HOLOGRAMS.put(player.getUniqueId(), hologramPair);
        } else {
            hologramPair.topLine.teleport(hologramLocation.clone().add(0.0D, 0.3D, 0.0D));
            hologramPair.bottomLine.teleport(hologramLocation);
        }

        hologramPair.topLine.setCustomName(topText);
        hologramPair.bottomLine.setCustomName(bottomText);
        updateVisibility(plugin, player, hologramPair.topLine);
        updateVisibility(plugin, player, hologramPair.bottomLine);
    }

    /**
     * Overload lama (legacy) — konversi remainingSeconds ke progress.
     * Dipertahankan agar backward-compatible dengan kode lama.
     */
    public static void update(Player player, Location blockLocation, double remainingSeconds, BlockRegen plugin) {
        // Tidak digunakan lagi; gunakan versi update(player, location, progress, plugin)
        remove(player);
    }

    public static void remove(Player player) {
        if (player == null) {
            return;
        }
        HologramPair hologramPair = ACTIVE_HOLOGRAMS.remove(player.getUniqueId());
        if (hologramPair != null) {
            hologramPair.remove();
        }
    }

    public static void removeAll() {
        for (HologramPair hologramPair : ACTIVE_HOLOGRAMS.values()) {
            if (hologramPair != null) {
                hologramPair.remove();
            }
        }
        ACTIVE_HOLOGRAMS.clear();
    }

    private static ArmorStand createLine(World world, Location location) {
        ArmorStand armorStand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setMarker(true);
        armorStand.setSmall(true);
        armorStand.setCustomNameVisible(true);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);
        return armorStand;
    }

    private static void updateVisibility(BlockRegen plugin, Player owner, ArmorStand armorStand) {
        World world = armorStand.getWorld();
        for (Player onlinePlayer : world.getPlayers()) {
            if (onlinePlayer.getUniqueId().equals(owner.getUniqueId())) {
                onlinePlayer.showEntity(plugin, armorStand);
            } else {
                onlinePlayer.hideEntity(plugin, armorStand);
            }
        }
    }

    private static final class HologramPair {
        private final ArmorStand topLine;
        private final ArmorStand bottomLine;

        private HologramPair(ArmorStand topLine, ArmorStand bottomLine) {
            this.topLine = topLine;
            this.bottomLine = bottomLine;
        }

        private boolean isValid() {
            return topLine != null && bottomLine != null && topLine.isValid() && bottomLine.isValid();
        }

        private void remove() {
            if (topLine != null && topLine.isValid()) {
                topLine.remove();
            }
            if (bottomLine != null && bottomLine.isValid()) {
                bottomLine.remove();
            }
        }
    }
}
