package me.allync.blockregen.data;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockData {

    private final Material replacedBlock;
    private final int regenDelay;
    private final Set<String> allowedRegions;
    private final List<ToolRequirement> requiredTools;
    private final boolean autoInventory;
    private final boolean naturalDrop;
    private final String breakSound;
    private final String regenSound;
    private final List<String> commands;
    private final Map<String, CustomDrop> customDrops;
    private final boolean singleCustomDropRoll;
    private final List<RegenVariant> regenVariants;
    private final String breakParticle;
    private final String regenParticle;
    private final String expDropAmount;
    private final boolean autoPickupExp;
    private final Map<String, Integer> mmocoreExp;
    private final Map<String, Integer> auraskillsXp;
    private final FortuneData fortuneData; // Diubah dari Map ke single object

    // --- BARU ---
    private final double breakDuration;
    private final boolean fixedDuration;
    // --- AKHIR BARU ---

    // --- AUTO-SCAN ---
    private final double autoScanActiveChance; // -1 = use global default from config
    // --- AKHIR AUTO-SCAN ---

    @SuppressWarnings("unchecked")
    public BlockData(ConfigurationSection section) {
        this.replacedBlock = Material.valueOf(section.getString("replaced-block", "STONE").toUpperCase());
        this.regenDelay = section.getInt("regen-delay", 5);
        this.allowedRegions = new HashSet<>();
        this.requiredTools = new ArrayList<>();

        for (String regionName : section.getStringList("regions")) {
            if (regionName != null && !regionName.trim().isEmpty()) {
                this.allowedRegions.add(regionName.toLowerCase());
            }
        }

        List<?> toolsList = section.getList("tools-required");
        if (toolsList != null) {
            for (Object toolObject : toolsList) {
                try {
                    String materialString = null;
                    Map<String, Object> detailsMap = null;

                    if (toolObject instanceof String) {
                        materialString = (String) toolObject;
                    } else if (toolObject instanceof Map) {
                        Map<String, Object> toolMap = (Map<String, Object>) toolObject;
                        if (toolMap.isEmpty()) continue;

                        materialString = toolMap.keySet().iterator().next();
                        detailsMap = (Map<String, Object>) toolMap.get(materialString);
                    }

                    if (materialString != null) {
                        String name = (detailsMap != null && detailsMap.containsKey("name")) ? (String) detailsMap.get("name") : null;
                        List<String> lore = (detailsMap != null && detailsMap.containsKey("lore")) ? (List<String>) detailsMap.get("lore") : null;

                        if (materialString.contains(":")) {
                            this.requiredTools.add(new ToolRequirement(materialString, name, lore));
                        } else {
                            Material material = Material.valueOf(materialString.toUpperCase());
                            this.requiredTools.add(new ToolRequirement(material, name, lore));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[BlockRegen] Gagal memuat salah satu tool requirement di blok '" + section.getName() + "'. Entri: " + toolObject.toString() + ". Error: " + e.getMessage());
                }
            }
        }

        this.autoInventory = section.getBoolean("drops.auto-inventory", false);
        this.naturalDrop = section.getBoolean("drops.natural-drop", true);
        this.breakSound = firstNonBlank(section,
                "sounds.break-sound",
                "sound.break-sound",
                "sound.break",
                "break-sound");
        this.regenSound = firstNonBlank(section,
                "sounds.regen-sound",
                "sound.regen-sound",
                "sound.regen",
                "regen-sound");
        this.commands = section.getStringList("commands");
        this.customDrops = new HashMap<>();
        ConfigurationSection customDropsSection = section.getConfigurationSection("custom-drops");
        if (customDropsSection != null) {
            for (String key : customDropsSection.getKeys(false)) {
                ConfigurationSection dropSection = customDropsSection.getConfigurationSection(key);
                if (dropSection != null) {
                    this.customDrops.put(key, new CustomDrop(dropSection));
                }
            }
        }
        // If true, only one custom drop entry is selected per block break.
        this.singleCustomDropRoll = section.getBoolean("drops.single-custom-drop-roll", false);

        this.regenVariants = new ArrayList<>();
        ConfigurationSection regenVariantsSection = section.getConfigurationSection("regen-blocks");
        if (regenVariantsSection == null) {
            regenVariantsSection = section.getConfigurationSection("regen-block-variants");
        }
        if (regenVariantsSection != null) {
            for (String key : regenVariantsSection.getKeys(false)) {
                ConfigurationSection variantSection = regenVariantsSection.getConfigurationSection(key);
                if (variantSection != null) {
                    String blockIdentifier = variantSection.getString("block", key);
                    double chance = variantSection.getDouble("chance", 0.0);
                    if (blockIdentifier != null && !blockIdentifier.isEmpty() && chance > 0.0) {
                        this.regenVariants.add(new RegenVariant(blockIdentifier, chance));
                    }
                } else {
                    Object raw = regenVariantsSection.get(key);
                    if (raw instanceof Number) {
                        double chance = ((Number) raw).doubleValue();
                        if (chance > 0.0) {
                            this.regenVariants.add(new RegenVariant(key, chance));
                        }
                    }
                }
            }
        }
        this.breakParticle = firstNonBlank(section,
                "particles.break-particle",
                "particle.break-particle",
                "particle.break",
                "break-particle");
        this.regenParticle = firstNonBlank(section,
                "particles.regen-particle",
                "particle.regen-particle",
                "particle.regen",
                "regen-particle");

        if (section.isSet("exp-drop-amount")) {
            this.expDropAmount = section.getString("exp-drop-amount", "0");
        } else {
            this.expDropAmount = getVanillaExpDrop(section.getName());
        }

        this.autoPickupExp = section.getBoolean("auto-pickup-exp", false);

        this.mmocoreExp = new HashMap<>();
        ConfigurationSection mmocoreSection = section.getConfigurationSection("mmocore-exp");
        if (mmocoreSection != null) {
            for (String professionId : mmocoreSection.getKeys(false)) {
                int exp = mmocoreSection.getInt(professionId);
                this.mmocoreExp.put(professionId.toLowerCase(), exp);
            }
        }

        this.auraskillsXp = new HashMap<>();
        ConfigurationSection auraSkillsSection = section.getConfigurationSection("auraskills-xp");
        if (auraSkillsSection != null) {
            for (String skillId : auraSkillsSection.getKeys(false)) {
                int exp = auraSkillsSection.getInt(skillId);
                this.auraskillsXp.put(skillId.toLowerCase(), exp);
            }
        }

        // --- PERUBAHAN ---
        // 'fortune' sekarang adalah satu objek, bukan map.
        ConfigurationSection fortuneSection = section.getConfigurationSection("fortune");
        if (fortuneSection != null) {
            this.fortuneData = new FortuneData(fortuneSection);
        } else {
            this.fortuneData = null; // Tidak ada konfigurasi fortune
        }
        // --- AKHIR PERUBAHAN ---

        // --- BARU ---
        this.breakDuration = section.getDouble("break-duration", 0.0);
        this.fixedDuration = section.getBoolean("fixed-duration", false);
        // --- AKHIR BARU ---

        // --- AUTO-SCAN ---
        this.autoScanActiveChance = section.isSet("auto-scan.active-chance")
                ? section.getDouble("auto-scan.active-chance", -1.0)
                : -1.0;
        // --- AKHIR AUTO-SCAN ---
    }

    public static class FortuneData {
        private final boolean enabled;
        private final Map<Integer, Double> multipliers;

        public FortuneData(ConfigurationSection section) {
            this.enabled = section.getBoolean("enabled", false);
            this.multipliers = new HashMap<>();
            ConfigurationSection multSection = section.getConfigurationSection("multiplier");
            if (multSection != null) {
                for (String key : multSection.getKeys(false)) {
                    try {
                        this.multipliers.put(Integer.parseInt(key), multSection.getDouble(key));
                    } catch (NumberFormatException e) {
                        System.err.println("[BlockRegen] Invalid fortune level key: " + key);
                    }
                }
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Map<Integer, Double> getMultipliers() {
            return multipliers;
        }
    }

    public static class RegenVariant {
        private final String blockIdentifier;
        private final double chance;

        public RegenVariant(String blockIdentifier, double chance) {
            this.blockIdentifier = blockIdentifier;
            this.chance = chance;
        }

        public String getBlockIdentifier() {
            return blockIdentifier;
        }

        public double getChance() {
            return chance;
        }
    }

    private String getVanillaExpDrop(String blockName) {
        switch (blockName.toUpperCase()) {
            case "COAL_ORE": case "DEEPSLATE_COAL_ORE": return "0-2";
            case "LAPIS_ORE": case "DEEPSLATE_LAPIS_ORE": return "2-5";
            case "REDSTONE_ORE": case "DEEPSLATE_REDSTONE_ORE": return "1-5";
            case "DIAMOND_ORE": case "DEEPSLATE_DIAMOND_ORE": return "3-7";
            case "EMERALD_ORE": case "DEEPSLATE_EMERALD_ORE": return "3-7";
            case "NETHER_QUARTZ_ORE": return "2-5";
            case "NETHER_GOLD_ORE": return "0-1";
            default: return "0";
        }
    }

    private String firstNonBlank(ConfigurationSection section, String... paths) {
        if (section == null || paths == null) {
            return null;
        }
        for (String path : paths) {
            String value = section.getString(path);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public Material getReplacedBlock() {
        return replacedBlock;
    }

    public int getRegenDelay() {
        return regenDelay;
    }

    public boolean hasRegionRestriction() {
        return !allowedRegions.isEmpty();
    }

    public boolean isRegionAllowed(String regionName) {
        if (!hasRegionRestriction()) {
            return true;
        }
        if (regionName == null || regionName.isEmpty()) {
            return false;
        }
        return allowedRegions.contains(regionName.toLowerCase());
    }

    public boolean isRegionAllowed(Collection<String> regionNames) {
        if (!hasRegionRestriction()) {
            return true;
        }
        if (regionNames == null || regionNames.isEmpty()) {
            return false;
        }
        for (String regionName : regionNames) {
            if (regionName != null && allowedRegions.contains(regionName.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getAllowedRegions() {
        return allowedRegions;
    }

    public List<ToolRequirement> getRequiredTools() {
        return requiredTools;
    }

    public boolean requiresTool() {
        return !requiredTools.isEmpty();
    }

    public boolean isAutoInventory() {
        return autoInventory;
    }

    public boolean isNaturalDrop() {
        return naturalDrop;
    }

    public String getBreakSound() {
        return breakSound;
    }

    public String getRegenSound() {
        return regenSound;
    }

    public List<String> getCommands() {
        return commands;
    }

    public boolean hasCustomDrops() {
        return !customDrops.isEmpty();
    }

    public Map<String, CustomDrop> getCustomDrops() {
        return customDrops;
    }

    public boolean isSingleCustomDropRoll() {
        return singleCustomDropRoll;
    }

    public boolean hasRegenVariants() {
        return !regenVariants.isEmpty();
    }

    public List<RegenVariant> getRegenVariants() {
        return regenVariants;
    }

    public String getBreakParticle() {
        return breakParticle;
    }

    public String getRegenParticle() {
        return regenParticle;
    }

    public String getExpDropAmount() {
        return expDropAmount;
    }

    public boolean isAutoPickupExp() {
        return autoPickupExp;
    }

    public Map<String, Integer> getMmocoreExp() {
        return mmocoreExp;
    }

    public boolean hasMmocoreExp() {
        return this.mmocoreExp != null && !this.mmocoreExp.isEmpty();
    }

    public Map<String, Integer> getAuraskillsXp() {
        return auraskillsXp;
    }

    public boolean hasAuraskillsXp() {
        return this.auraskillsXp != null && !this.auraskillsXp.isEmpty();
    }

    public FortuneData getFortuneData() {
        return fortuneData;
    }

    // --- GETTER BARU ---
    public double getBreakDuration() {
        return breakDuration;
    }

    public boolean isFixedDuration() {
        return fixedDuration;
    }

    public boolean hasCustomBreakDuration() {
        return this.breakDuration > 0.0;
    }
    // --- AKHIR GETTER BARU ---

    // --- GETTER AUTO-SCAN ---
    /**
     * Returns the per-block active-chance for the Auto-Scan system.
     * Returns -1 if not configured (caller should use the global default).
     */
    public double getAutoScanActiveChance() {
        return autoScanActiveChance;
    }
    // --- AKHIR GETTER AUTO-SCAN ---
}