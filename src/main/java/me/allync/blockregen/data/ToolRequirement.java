package me.allync.blockregen.data;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public class ToolRequirement {
    private final Material material;
    private final String customMaterialId; // Untuk menyimpan ID custom seperti "mmoitems:TOOL:XENG_LV_1"
    private final String name;
    private final List<String> lore;

    // Konstruktor untuk item vanilla
    public ToolRequirement(Material material, String name, List<String> lore) {
        this.material = material;
        this.customMaterialId = null;
        this.name = name;
        this.lore = lore;
    }

    // Konstruktor untuk item custom (MMOItems, ItemsAdder, dll)
    public ToolRequirement(String customMaterialId, String name, List<String> lore) {
        this.material = null; // Tidak ada material vanilla
        this.customMaterialId = customMaterialId;
        this.name = name;
        this.lore = lore;
    }

    /**
     * Memeriksa apakah ItemStack yang diberikan memenuhi persyaratan ini.
     * @param item ItemStack yang dipegang pemain.
     * @return true jika cocok, false jika tidak.
     */
    public boolean matches(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // --- PEMERIKSAAN MATERIAL ---
        if (this.material != null) { // Persyaratan adalah item VANILLA
            if (item.getType() != this.material) {
                return false;
            }
        } else if (this.customMaterialId != null) { // Persyaratan adalah item CUSTOM
            if (this.customMaterialId.startsWith("mmoitems:")) {
                String mmoType = MMOItems.getTypeName(item);
                String mmoId = MMOItems.getID(item);

                if (mmoType == null || mmoId == null) return false;

                String requiredId = this.customMaterialId; // e.g., "mmoitems:TOOL:XENG_LV_1"
                String[] parts = requiredId.split(":");
                if (parts.length != 3) return false; // Format tidak valid

                String requiredType = parts[1];
                String requiredMmoId = parts[2];

                if (!mmoType.equalsIgnoreCase(requiredType) || !mmoId.equalsIgnoreCase(requiredMmoId)) {
                    return false;
                }
            }
            // Tambahkan logika untuk ItemsAdder di sini jika diperlukan
            // else if (this.customMaterialId.startsWith("itemsadder:")) { ... }
            else {
                return false; // Format custom tidak dikenali
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null && (this.name != null || (this.lore != null && !this.lore.isEmpty()))) {
            // Item tidak punya meta, tapi persyaratan butuh nama/lore, jadi gagal.
            return false;
        }

        // --- PEMERIKSAAN NAMA (Opsional) ---
        if (this.name != null) {
            if (meta == null || !meta.hasDisplayName() || !meta.getDisplayName().equals(me.allync.blockregen.util.ColorUtil.color(this.name))) {
                return false;
            }
        }

        // --- PEMERIKSAAN LORE (Opsional) ---
        if (this.lore != null && !this.lore.isEmpty()) {
            if (meta == null || !meta.hasLore() || meta.getLore() == null) {
                return false;
            }
            List<String> itemLore = meta.getLore();
            List<String> requiredLore = this.lore.stream()
                    .map(line -> me.allync.blockregen.util.ColorUtil.color(line))
                    .collect(Collectors.toList());

            if (!itemLore.containsAll(requiredLore)) {
                return false;
            }
        }

        // Jika semua pemeriksaan lolos
        return true;
    }

    @Override
    public String toString() {
        return "ToolRequirement{" +
                "material=" + (material != null ? material.name() : "null") +
                ", customMaterialId='" + customMaterialId + '\'' +
                ", name='" + name + '\'' +
                ", lore=" + lore +
                '}';
    }

    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return ChatColor.stripColor(me.allync.blockregen.util.ColorUtil.color(name));
        }

        if (material != null) {
            return prettify(material.name());
        }

        if (customMaterialId != null && !customMaterialId.isEmpty()) {
            String[] parts = customMaterialId.split(":");
            String base = parts[parts.length - 1];
            return prettify(base);
        }

        return "Tool";
    }

    private String prettify(String value) {
        String lowered = value.toLowerCase().replace('_', ' ');
        String[] words = lowered.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.isEmpty() ? value : builder.toString();
    }
}
