package me.allync.blockregen.util;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.data.TextHologramData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class FancyHologramsHelper {

    private FancyHologramsHelper() {}

    public static void createOrUpdateDurationHologram(Player player, Location location, String topText, String bottomText) {
        if (player == null || location == null) return;
        String name = "blockregen_duration_" + player.getUniqueId();
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Hologram hologram = manager.getHologram(name).orElse(null);

        if (hologram == null) {
            TextHologramData data = new TextHologramData(name, location);
            data.setText(Arrays.asList(topText, bottomText));
            data.setPersistent(false);
            hologram = manager.create(data);
            manager.addHologram(hologram);
        } else {
            hologram.getData().setLocation(location);
            hologram.getData().setPersistent(false);
            if (hologram.getData() instanceof TextHologramData textData) {
                textData.setText(Arrays.asList(topText, bottomText));
            }
            hologram.queueUpdate();
        }

        // Handle visibility
        for (Player onlinePlayer : player.getWorld().getPlayers()) {
            if (onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                hologram.showHologram(onlinePlayer);
            } else {
                hologram.hideHologram(onlinePlayer);
            }
        }
    }

    public static void removeDurationHologram(Player player) {
        if (player == null) return;
        String name = "blockregen_duration_" + player.getUniqueId();
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(name).ifPresent(manager::removeHologram);
    }

    public static void removeAllDurationHolograms() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        List<Hologram> toRemove = new ArrayList<>();
        for (Hologram holo : manager.getHolograms()) {
            if (holo.getData().getName().startsWith("blockregen_duration_")) {
                toRemove.add(holo);
            }
        }
        for (Hologram holo : toRemove) {
            manager.removeHologram(holo);
        }
    }

    public static void createOrUpdateHealthHologram(Location location, String topText, String bottomText) {
        if (location == null) return;
        String name = "blockregen_health_" + location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Hologram hologram = manager.getHologram(name).orElse(null);

        if (hologram == null) {
            TextHologramData data = new TextHologramData(name, location);
            data.setText(Arrays.asList(topText, bottomText));
            data.setPersistent(false);
            hologram = manager.create(data);
            manager.addHologram(hologram);
        } else {
            hologram.getData().setLocation(location);
            hologram.getData().setPersistent(false);
            if (hologram.getData() instanceof TextHologramData textData) {
                textData.setText(Arrays.asList(topText, bottomText));
            }
            hologram.queueUpdate();
        }
    }

    public static void removeHealthHologram(Location location) {
        if (location == null) return;
        String name = "blockregen_health_" + location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(name).ifPresent(manager::removeHologram);
    }

    public static void removeAllHealthHolograms() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        List<Hologram> toRemove = new ArrayList<>();
        for (Hologram holo : manager.getHolograms()) {
            if (holo.getData().getName().startsWith("blockregen_health_")) {
                toRemove.add(holo);
            }
        }
        for (Hologram holo : toRemove) {
            manager.removeHologram(holo);
        }
    }
}
