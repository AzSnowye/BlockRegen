package me.allync.blockregen.util;

import org.bukkit.ChatColor;

public final class ColorUtil {
    private static boolean miniMessageAvailable = false;
    
    static {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            miniMessageAvailable = true;
        } catch (ClassNotFoundException ignored) {
        }
    }

    private ColorUtil() {}

    public static String color(String text) {
        if (text == null) return null;
        
        String translated = ChatColor.translateAlternateColorCodes('&', text);
        
        if (miniMessageAvailable) {
            try {
                return MiniMessageParser.parse(translated);
            } catch (Throwable t) {
                // Abaikan error jika class tidak cocok (misalnya versi usang)
            }
        }
        
        return translated;
    }

    /**
     * Inner class agar tidak terjadi ClassNotFoundException saat class ColorUtil dimuat
     * di server yang tidak memiliki library kyori adventure.
     */
    private static class MiniMessageParser {
        static String parse(String text) {
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text);
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build()
                    .serialize(component);
        }
    }
}
