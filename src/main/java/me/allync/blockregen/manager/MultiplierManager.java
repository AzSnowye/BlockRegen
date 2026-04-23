package me.allync.blockregen.manager;

import me.allync.blockregen.BlockRegen;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class MultiplierManager {

    private final BlockRegen plugin;
    private FileConfiguration multiplierConfig;

    private final Map<String, MultiplierProfile> profiles = new HashMap<>();
    private MultiplierProfile defaultProfile;

    // GUI settings (Global)
    public String guiTitle;
    public int guiSize;
    public boolean fillItemEnabled;
    public Material fillItemMaterial;
    public String fillItemName;
    public Map<String, ConfigItem> guiItems = new HashMap<>();

    // Messages (Global)
    public String successMessage;
    public String notEnoughMoneyMessage;
    public String adminSetSuccessMessage;
    public String adminSetNotifyMessage;
    public String adminPlayerNotFoundMessage;
    public String adminInvalidLevelMessage;
    public String adminUsageMessage;
    public String adminProfileNotFoundMessage;
    private final DecimalFormat df = new DecimalFormat("#,###.##");

    public static class ConfigItem {
        public int slot;
        public Material material;
        public String name;
        public List<String> lore;

        public ConfigItem(int slot, Material material, String name, List<String> lore) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
        }
    }

    public static class MultiplierProfile {
        private final String name;
        private final boolean enabled;
        private final int maxLevel;
        private final String balanceType;
        private final String coinEngineType;
        private final Map<Integer, Double> costs = new HashMap<>();
        private final Map<Integer, Double> multipliers = new HashMap<>();

        public MultiplierProfile(String name, ConfigurationSection section) {
            this.name = name;
            this.enabled = section.getBoolean("enabled", false);
            this.maxLevel = section.getInt("max-level", 10);
            this.balanceType = section.getString("balance-type", "Vault");
            this.coinEngineType = section.getString("coin-engine-type", "Money");

            ConfigurationSection costsSection = section.getConfigurationSection("costs");
            if (costsSection != null) {
                for (String key : costsSection.getKeys(false)) {
                    costs.put(Integer.parseInt(key), costsSection.getDouble(key));
                }
            }

            ConfigurationSection multipliersSection = section.getConfigurationSection("multipliers");
            if (multipliersSection != null) {
                for (String key : multipliersSection.getKeys(false)) {
                    multipliers.put(Integer.parseInt(key), multipliersSection.getDouble(key));
                }
            }
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public int getMaxLevel() { return maxLevel; }
        public double getCost(int level) { return costs.getOrDefault(level, 0.0); }
        public double getMultiplier(int level) { return multipliers.getOrDefault(level, 1.0); }
        public String getBalanceType() { return balanceType; }
        public String getCoinEngineType() { return coinEngineType; }
    }

    public MultiplierManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File multiplierFile = new File(plugin.getDataFolder(), "multiplier.yml");
        if (!multiplierFile.exists()) {
            plugin.saveResource("multiplier.yml", false);
        }
        this.multiplierConfig = YamlConfiguration.loadConfiguration(multiplierFile);

        // Load profiles
        profiles.clear();
        ConfigurationSection profilesSection = multiplierConfig.getConfigurationSection("multiplier-profiles");
        if (profilesSection != null) {
            for (String profileName : profilesSection.getKeys(false)) {
                ConfigurationSection profileSection = profilesSection.getConfigurationSection(profileName);
                if (profileSection != null) {
                    profiles.put(profileName.toLowerCase(), new MultiplierProfile(profileName, profileSection));
                }
            }
        }

        // Ensure default profile exists
        this.defaultProfile = profiles.get("default");
        if (this.defaultProfile == null) {
            plugin.getLogger().severe("Multiplier profile 'default' not found in multiplier.yml! The multiplier system will be disabled.");
            // Create a dummy profile to avoid NullPointerException
            this.defaultProfile = new MultiplierProfile("default", new YamlConfiguration().createSection("dummy"));
        }

        // Load global GUI settings
        this.guiTitle = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("gui.title", "&8Multiplier GUI"));
        this.guiSize = multiplierConfig.getInt("gui.size", 27);

        this.fillItemEnabled = multiplierConfig.getBoolean("gui.fill-item.enabled", false);
        try {
            this.fillItemMaterial = Material.valueOf(multiplierConfig.getString("gui.fill-item.material", "BLACK_STAINED_GLASS_PANE").toUpperCase());
        } catch (IllegalArgumentException e) {
            this.fillItemMaterial = Material.BLACK_STAINED_GLASS_PANE;
        }
        this.fillItemName = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("gui.fill-item.name", " "));

        this.guiItems.clear();
        ConfigurationSection itemsSection = multiplierConfig.getConfigurationSection("gui.items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                String path = "gui.items." + key;
                int slot = multiplierConfig.getInt(path + ".slot");
                Material mat;
                try {
                    mat = Material.valueOf(multiplierConfig.getString(path + ".material").toUpperCase());
                } catch (Exception e) {
                    mat = Material.STONE;
                    plugin.getLogger().warning("Invalid material for GUI item '" + key + "' in multiplier.yml.");
                }
                String name = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString(path + ".name"));
                List<String> lore = multiplierConfig.getStringList(path + ".lore").stream()
                        .map(line -> me.allync.blockregen.util.ColorUtil.color(line))
                        .collect(Collectors.toList());
                guiItems.put(key, new ConfigItem(slot, mat, name, lore));
            }
        }

        // Load global messages
        this.successMessage = me.allync.blockregen.util.ColorUtil.color(
                multiplierConfig.getString("messages.success-message", "&aUpgrade successful!"));
        this.notEnoughMoneyMessage = me.allync.blockregen.util.ColorUtil.color(
                multiplierConfig.getString("messages.not-enough-money-message", "&cNot enough money!"));
        this.adminSetSuccessMessage = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("messages.admin.set-success", "&aSuccess!"));
        this.adminSetNotifyMessage = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("messages.admin.set-notify", "&aYour level was set."));
        this.adminPlayerNotFoundMessage = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("messages.admin.player-not-found", "&cPlayer not found."));
        this.adminInvalidLevelMessage = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("messages.admin.invalid-level", "&cInvalid level."));
        this.adminUsageMessage = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("messages.admin.usage", "&cUsage: /rm set <player> <profile> <level>"));
        this.adminProfileNotFoundMessage = me.allync.blockregen.util.ColorUtil.color(multiplierConfig.getString("messages.admin.profile-not-found", "&cProfile not found."));

    }

    public MultiplierProfile getProfile(String profileName) {
        return profiles.getOrDefault(profileName.toLowerCase(), defaultProfile);
    }

    public MultiplierProfile getProfileForPlayer(Player player) {
        String profileName = plugin.getConfigManager().getMultiplierProfileForWorld(player.getWorld().getName());
        return getProfile(profileName);
    }

    public boolean isAnyProfileEnabled() {
        return profiles.values().stream().anyMatch(MultiplierProfile::isEnabled);
    }

    public Set<String> getProfileNames() {
        return profiles.keySet();
    }

    public String getFormattedCost(MultiplierProfile profile, int level) {
        String formattedCost = df.format(profile.getCost(level));
        if ("Vault".equalsIgnoreCase(profile.getBalanceType())) {
            return "$" + formattedCost;
        } else {
            return formattedCost + " " + profile.getCoinEngineType();
        }
    }

    public ConfigItem getItem(String key) {
        return guiItems.get(key);
    }
}

