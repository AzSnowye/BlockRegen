package me.allync.blockregen.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

public class SoundUtil {

    private static final class ParsedSound {
        private final Sound sound;
        private final float volume;
        private final float pitch;

        private ParsedSound(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static void playSound(Location location, String customSound, String defaultSound) {
        String soundString = (customSound != null && !customSound.isEmpty()) ? customSound : defaultSound;
        if (soundString == null || soundString.isEmpty()) return;

        ParsedSound parsedSound = parseSound(soundString);
        if (parsedSound == null) {
            return;
        }

        World world = location.getWorld();
        if (world != null) {
            world.playSound(location, parsedSound.sound, parsedSound.volume, parsedSound.pitch);
        }
    }

    public static void playSoundToPlayer(Player player, Location location, String customSound, String defaultSound) {
        if (player == null || !player.isOnline() || location == null) {
            return;
        }

        String soundString = (customSound != null && !customSound.isEmpty()) ? customSound : defaultSound;
        if (soundString == null || soundString.isEmpty()) {
            return;
        }

        ParsedSound parsedSound = parseSound(soundString);
        if (parsedSound == null) {
            return;
        }

        player.playSound(location, parsedSound.sound, parsedSound.volume, parsedSound.pitch);
    }

    public static void stopSoundToPlayer(Player player, String customSound, String defaultSound) {
        if (player == null || !player.isOnline()) {
            return;
        }

        String soundString = (customSound != null && !customSound.isEmpty()) ? customSound : defaultSound;
        if (soundString == null || soundString.isEmpty()) {
            return;
        }

        ParsedSound parsedSound = parseSound(soundString);
        if (parsedSound == null) {
            return;
        }

        player.stopSound(parsedSound.sound);
    }

    private static ParsedSound parseSound(String soundString) {
        try {
            ParsedInput input = parseInput(soundString);
            Sound sound = resolveSound(input.soundId);
            if (sound == null) {
                throw new IllegalArgumentException("Unknown sound: " + input.soundId);
            }
            return new ParsedSound(sound, input.volume, input.pitch);
        } catch (IllegalArgumentException e) {
            System.err.println("[BlockRegen] Invalid sound format: " + soundString);
            return null;
        }
    }

    private static ParsedInput parseInput(String raw) {
        String[] tokens = raw.split(":");
        if (tokens.length == 0) {
            throw new IllegalArgumentException("Empty sound format");
        }

        float volume = 1.0f;
        float pitch = 1.0f;
        int soundTokenEnd = tokens.length;

        if (tokens.length >= 2) {
            if (isFloat(tokens[tokens.length - 1])) {
                pitch = Float.parseFloat(tokens[tokens.length - 1]);
                soundTokenEnd = tokens.length - 1;
            }
            if (soundTokenEnd >= 2 && isFloat(tokens[soundTokenEnd - 1])) {
                volume = Float.parseFloat(tokens[soundTokenEnd - 1]);
                soundTokenEnd--;
            }
        }

        if (soundTokenEnd <= 0) {
            throw new IllegalArgumentException("Missing sound id");
        }

        StringBuilder soundIdBuilder = new StringBuilder(tokens[0].trim());
        for (int i = 1; i < soundTokenEnd; i++) {
            soundIdBuilder.append(':').append(tokens[i].trim());
        }

        String soundId = soundIdBuilder.toString();
        if (soundId.isEmpty()) {
            throw new IllegalArgumentException("Missing sound id");
        }

        return new ParsedInput(soundId, volume, pitch);
    }

    private static Sound resolveSound(String soundId) {
        try {
            return Sound.valueOf(soundId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Not an enum constant, try namespaced registry key next.
        }

        String normalized = soundId.toLowerCase(Locale.ROOT);
        String namespaced = normalized.contains(":") ? normalized : "minecraft:" + normalized;
        NamespacedKey key = NamespacedKey.fromString(namespaced);
        if (key == null) {
            return null;
        }
        return Registry.SOUNDS.get(key);
    }

    private static boolean isFloat(String value) {
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static final class ParsedInput {
        private final String soundId;
        private final float volume;
        private final float pitch;

        private ParsedInput(String soundId, float volume, float pitch) {
            this.soundId = soundId;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
}