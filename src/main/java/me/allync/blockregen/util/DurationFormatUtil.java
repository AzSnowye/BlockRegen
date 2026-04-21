package me.allync.blockregen.util;

/**
 * Formats duration values into Indonesian human-readable text.
 */
public final class DurationFormatUtil {

    private DurationFormatUtil() {
        // Utility class
    }

    public static String formatDurationSeconds(double totalSeconds) {
        long seconds = Math.max(0L, (long) Math.ceil(totalSeconds));

        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;

        if (hours > 0L) {
            StringBuilder builder = new StringBuilder();
            builder.append(hours).append(" jam");
            if (minutes > 0L) {
                builder.append(' ').append(minutes).append(" menit");
            }
            if (remainingSeconds > 0L) {
                builder.append(' ').append(remainingSeconds).append(" detik");
            }
            return builder.toString();
        }

        if (minutes > 0L) {
            if (remainingSeconds > 0L) {
                return minutes + " menit " + remainingSeconds + " detik";
            }
            return minutes + " menit";
        }

        return remainingSeconds + " detik";
    }
}

