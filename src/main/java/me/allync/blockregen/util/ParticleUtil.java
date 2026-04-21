package me.allync.blockregen.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Locale;

public class ParticleUtil {

    public static void spawnParticle(Location location, String particleString) {
        if (location == null || particleString == null || particleString.isEmpty()) {
            return;
        }

        String[] parts = particleString.split(":");
        if (parts.length < 1) {
            return;
        }

        Particle particle = resolveParticle(parts[0]);
        if (particle == null) {
            System.err.println("[BlockRegen] Invalid particle format: " + particleString);
            return;
        }

        int count = 1;
        if (parts.length > 1) {
            try {
                count = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                // Use default count
            }
        }

        double offset = 0.0;
        if (parts.length > 2) {
            try {
                offset = Double.parseDouble(parts[2]);
            } catch (NumberFormatException ignored) {
                // Use default offset
            }
        }

        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(particle, location, count, offset, offset, offset);
        }
    }

    private static Particle resolveParticle(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        String normalized = raw.trim();
        if (normalized.contains(":")) {
            normalized = normalized.substring(normalized.indexOf(':') + 1);
        }

        normalized = normalized.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);

        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
