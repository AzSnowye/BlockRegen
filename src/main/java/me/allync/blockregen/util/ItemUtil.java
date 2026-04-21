package me.allync.blockregen.util;

import dev.lone.itemsadder.api.CustomStack;
import me.allync.blockregen.BlockRegen;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import me.allync.blockregen.data.CustomDrop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ItemUtil {

    private static boolean mmoItemsEnabled = false;
    private static boolean nexoEnabled = false;

    public static void setMmoItemsEnabled(boolean enabled) {
        mmoItemsEnabled = enabled;
    }

    public static void setNexoEnabled(boolean enabled) {
        nexoEnabled = enabled;
    }

    /**
     * Membuat sebuah ItemStack dari objek CustomDrop.
     * Metode ini menggantikan getCustomDrops() untuk menangani pembuatan item satu per satu.
     * @param customDrop Objek CustomDrop yang akan dibuat menjadi item.
     * @return ItemStack yang telah dikonfigurasi, atau null jika terjadi kesalahan.
     */
    public static ItemStack createItemStack(CustomDrop customDrop) {
        if (customDrop == null) {
            return null;
        }
        String materialString = customDrop.getMaterial();
        if (materialString == null || materialString.isEmpty()) {
            return null;
        }

        ItemStack item = null;

        if (nexoEnabled && materialString.toLowerCase().startsWith("nexo:")) {
            item = NexoUtil.createNexoItemStack(materialString);
            if (item == null) {
                System.err.println("[BlockRegen] Tidak dapat menemukan item Nexo dengan ID '" + materialString + "'.");
                return null;
            }
        } else if (mmoItemsEnabled && materialString.toLowerCase().startsWith("mmoitems:")) {
            String[] parts = materialString.split(":");
            if (parts.length == 3) { // Format: "mmoitems:TYPE:ID"
                String typeName = parts[1];
                String id = parts[2];
                Type type = MMOItems.plugin.getTypes().get(typeName.toUpperCase());
                if (type != null) {
                    item = MMOItems.plugin.getItem(type, id);
                } else {
                    System.err.println("[BlockRegen] Tipe MMOItems tidak valid '" + typeName + "' untuk ID '" + id + "'.");
                }
            } else {
                System.err.println("[BlockRegen] Format MMOItems tidak valid: " + materialString + ". Seharusnya 'mmoitems:TYPE:ID'.");
            }
        } else if (BlockRegen.itemsAdderEnabled && materialString.contains(":")) {
            String[] parts = materialString.split(":");
            if (parts.length == 2) {
                String id = parts[1];
                CustomStack customStack = CustomStack.getInstance(id);
                if (customStack != null) {
                    item = customStack.getItemStack();
                } else {
                    System.err.println("[BlockRegen] Tidak dapat menemukan item ItemsAdder dengan ID '" + id + "'.");
                }
            }
            // Menangani item vanilla
        } else {
            try {
                Material material = Material.valueOf(materialString.toUpperCase());
                item = new ItemStack(material);
            } catch (IllegalArgumentException e) {
                System.err.println("[BlockRegen] Material tidak valid atau format item kustom tidak ditangani: " + materialString);
                return null;
            }
        }

        if (item == null) {
            if (!materialString.toLowerCase().startsWith("mmoitems:") && !(BlockRegen.itemsAdderEnabled && materialString.contains(":")) && !(nexoEnabled && materialString.toLowerCase().startsWith("nexo:"))) {
                System.err.println("[BlockRegen] Gagal membuat item dari string material: " + materialString);
            }
            return null;
        }

        int amount = parseAmount(customDrop.getAmount());
        item.setAmount(amount);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (customDrop.hasName()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', customDrop.getName()));
            }
            if (customDrop.hasLore()) {
                List<String> lore = customDrop.getLore().stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Mengurai string jumlah, bisa berupa angka tunggal atau rentang (misal, "1-5").
     * @param amountStr String jumlah.
     * @return Jumlah acak dalam rentang atau angka tunggal.
     */
    private static int parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            return 1;
        }
        if (amountStr.contains("-")) {
            String[] parts = amountStr.split("-");
            try {
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);
                if (min >= max) return min;
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return 1;
            }
        }
        try {
            return Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
