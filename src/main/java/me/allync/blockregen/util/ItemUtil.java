package me.allync.blockregen.util;

import dev.lone.itemsadder.api.CustomStack;
import me.allync.blockregen.BlockRegen;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import me.allync.blockregen.data.CustomDrop;
import net.Indyuce.mmoitems.api.player.PlayerData;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

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
                meta.setDisplayName(me.allync.blockregen.util.ColorUtil.color(customDrop.getName()));
            }
            if (customDrop.hasLore()) {
                List<String> lore = customDrop.getLore().stream()
                        .map(me.allync.blockregen.util.ColorUtil::color)
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

    /**
     * Mendapatkan nilai Pickaxe Power dari item MMOItems (melalui MythicLib NBT).
     * @param player Player yang memegang item.
     * @param item Item yang akan dicek.
     * @return Nilai Pickaxe Power, atau -1 jika tidak memenuhi syarat, atau 0.0 jika tidak ada.
     */
    public static double getPickaxePower(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0.0;
        }

        // Cek persyaratan MMOItems jika diaktifkan
        if (mmoItemsEnabled) {
            // Pastikan NBTItem dapat di-parse sebelum melanjutkan
            io.lumine.mythic.lib.api.item.NBTItem nbtItem = io.lumine.mythic.lib.api.item.NBTItem.get(item);
            if (nbtItem.hasType()) { // Hanya cek jika ini adalah MMOItem
                PlayerData playerData = PlayerData.get(player);
                if (!playerData.getRPG().canUse(nbtItem, true)) {
                    return -1; // Indikator bahwa pemain tidak dapat menggunakan item
                }
            }
        }

        // Jika MMOItems tidak diaktifkan, atau jika item bukan MMOItem, lanjutkan ke logika power
        if (!mmoItemsEnabled) {
            return fallbackVanillaPower(item);
        }

        try {
            io.lumine.mythic.lib.api.item.NBTItem nbtItem = io.lumine.mythic.lib.api.item.NBTItem.get(item);

            // Jika bukan MMOItem, gunakan power vanilla
            if (!nbtItem.hasType()) {
                return fallbackVanillaPower(item);
            }

            double power = 0.0D;

            // Metode 1: Cek langsung via NBT tag (paling cepat)
            if (nbtItem.hasTag("MMOITEMS_PICKAXE_POWER")) {
                double val = nbtItem.getDouble("MMOITEMS_PICKAXE_POWER");
                power = Math.max(power, val);
            }

            // Metode 2: Fallback ke LiveMMOItem API (lebih lambat, tapi lebih lengkap)
            try {
                LiveMMOItem liveItem = new LiveMMOItem(item);
                if (liveItem.hasData(ItemStats.PICKAXE_POWER)) {
                    StatData data = liveItem.getData(ItemStats.PICKAXE_POWER);
                    if (data instanceof DoubleData) {
                        double val = ((DoubleData) data).getValue();
                        power = Math.max(power, val);
                    }
                }

                // Beberapa setup MMOItems memakai alias stat custom untuk mining.
                power = Math.max(power, readCustomMmoItemsDoubleStat(liveItem, "MINING_EFFICIENCY"));
                power = Math.max(power, readCustomMmoItemsDoubleStat(liveItem, "MINING_SPEED"));
            } catch (Exception e2) {
                // System.err.println("[BlockRegen] LiveMMOItem fallback gagal untuk power: " + e2.getMessage());
            }

            if (power > 0.0D) {
                return power;
            }

            return fallbackVanillaPower(item);

        } catch (NoClassDefFoundError | Exception e) {
            // System.err.println("[BlockRegen] Error membaca pickaxe power: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return fallbackVanillaPower(item); // Fallback jika ada error
    }

    private static double readCustomMmoItemsDoubleStat(LiveMMOItem liveItem, String statId) {
        if (liveItem == null || statId == null || statId.isEmpty()) {
            return 0.0D;
        }

        try {
            ItemStat<?, ?> stat = MMOItems.plugin.getStats().get(statId);
            if (stat == null || !liveItem.hasData(stat)) {
                return 0.0D;
            }

            StatData data = liveItem.getData(stat);
            if (data instanceof DoubleData doubleData) {
                return doubleData.getValue();
            }
        } catch (Throwable ignored) {
        }

        return 0.0D;
    }

    private static double fallbackVanillaPower(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0.0D;
        }

        double basePower = 0.0D;
        
        if (mmoItemsEnabled) {
            try {
                if (MMOItems.plugin != null && MMOItems.plugin.getConfig() != null) {
                    org.bukkit.configuration.ConfigurationSection section = MMOItems.plugin.getConfig().getConfigurationSection("default-pickaxe-power");
                    if (section != null) {
                        basePower = section.getDouble(item.getType().name(), 0.0D);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        
        if (basePower == 0.0D) {
            String name = item.getType().name();
            if (name.endsWith("_PICKAXE")) {
                if (name.startsWith("WOODEN")) basePower = 5.0D;
                else if (name.startsWith("STONE")) basePower = 8.0D;
                else if (name.startsWith("COPPER")) basePower = 12.0D;
                else if (name.startsWith("GOLDEN")) basePower = 15.0D;
                else if (name.startsWith("IRON")) basePower = 12.0D;
                else if (name.startsWith("DIAMOND")) basePower = 18.0D;
                else if (name.startsWith("NETHERITE")) basePower = 22.0D;
            }
        }

        int efficiencyLevel = item.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiencyLevel > 0) {
            // Efisiensi vanilla ditambahkan ke power agar block-health tetap bisa dipakai
            // di server yang tidak memakai stat MMOItems custom.
            basePower += efficiencyLevel * 5.0D;
        }

        return basePower;
    }
}
