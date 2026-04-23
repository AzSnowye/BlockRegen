package me.allync.blockregen.manager;

import me.allync.blockregen.BlockRegen;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigManager {

    private final BlockRegen plugin;
    private FileConfiguration config;
    private final int LATEST_CONFIG_VERSION = 9;

    public String prefix;
    public String reloadMessage;
    public String noPermissionMessage;
    public String inventoryFullMessage;
    public String wrongToolMessage;
    public String blockBeingMinedMessage;
    public String touchLimitReachedMessage;
    public String regenCountdownMessage;
    public String wandReceiveMessage;
    public String pos1Message;
    public String pos2Message;
    public String regionSaveMessage;
    public String notInRegionMessage;
    public String regionRemovedMessage;
    public String regionNotFoundMessage;
    public String updateNotifyMessage;
    public String helpHeader;
    public String helpTitle;
    public String helpFooter;
    public List<String> helpCommands;
    public Material wandMaterial;
    public String wandName;
    public String defaultBreakSound;
    public String defaultRegenSound;
    public String wrongToolSound;
    public boolean allWorldsEnabled;
    public List<String> enabledWorlds;
    public Map<String, String> worldMultiplierProfiles; // BARU
    public boolean sendRegenCountdown;
    public boolean checkForUpdates;
    public boolean preventMiningWhenFull;
    public boolean worldGuardEnabled;
    public boolean worldGuardDisableOtherBreak;
    public boolean worldGuardBreakRegenInDenyRegions;
    public boolean mmocoreEnabled;
    public double mmocoreHologramOffsetX;
    public double mmocoreHologramOffsetY;
    public double mmocoreHologramOffsetZ;
    public boolean particlesEnabled;
    public boolean particlesOnBreak;
    public boolean particlesOnRegen;
    public String defaultBreakParticle;
    public String defaultRegenParticle;
    public long miningHoldMineTimeoutMs;
    public long miningReleaseGraceMs;
    public long miningResumeTimeoutMs;
    public int miningDefaultTouchLimit;
    public boolean breakDurationHologramEnabled;
    public String breakDurationHologramText;
    public double breakDurationHologramOffsetX;
    public double breakDurationHologramOffsetY;
    public double breakDurationHologramOffsetZ;

    public ConfigManager(BlockRegen plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        updateConfig();
        loadValues();
    }

    private void updateConfig() {
        int currentVersion = config.getInt("config-version", 0);
        if (currentVersion < LATEST_CONFIG_VERSION) {
            plugin.getLogger().info("Your config.yml is outdated. Updating to the latest version...");
            Configuration defaultConfig = plugin.getConfig().getDefaults();
            if (defaultConfig != null) {
                for (String key : defaultConfig.getKeys(true)) {
                    if (!config.isSet(key)) {
                        config.set(key, defaultConfig.get(key));
                        plugin.getLogger().info("Added missing config option: " + key);
                    }
                }
            }
            config.set("config-version", LATEST_CONFIG_VERSION);
            try {
                config.save(new File(plugin.getDataFolder(), "config.yml"));
                plugin.getLogger().info("The config.yml has been updated successfully.");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save the updated config.yml!");
                e.printStackTrace();
            }
            plugin.reloadConfig();
            this.config = plugin.getConfig();
        }
    }

    private void loadValues() {
        this.prefix = getColoredString("messages.prefix");
        this.reloadMessage = this.prefix + getColoredString("messages.reload");
        this.noPermissionMessage = this.prefix + getColoredString("messages.no-permission");
        this.inventoryFullMessage = this.prefix + getColoredString("messages.inventory-full");
        this.wrongToolMessage = this.prefix + getColoredString("messages.wrong-tool");
        this.blockBeingMinedMessage = this.prefix + getColoredString("messages.block-being-mined");
        this.touchLimitReachedMessage = this.prefix + getColoredString("messages.touch-limit-reached");
        this.regenCountdownMessage = getColoredString("messages.regen-countdown");
        this.wandReceiveMessage = this.prefix + getColoredString("messages.wand-receive");
        this.pos1Message = getColoredString("messages.pos1-set");
        this.pos2Message = getColoredString("messages.pos2-set");
        this.regionSaveMessage = this.prefix + getColoredString("messages.region-saved");
        this.notInRegionMessage = this.prefix + getColoredString("messages.not-in-region");
        this.regionRemovedMessage = this.prefix + getColoredString("messages.region-removed");
        this.regionNotFoundMessage = this.prefix + getColoredString("messages.region-not-found");
        this.updateNotifyMessage = this.prefix + getColoredString("messages.update-notify");
        this.helpHeader = getColoredString("messages.help-header");
        this.helpTitle = getColoredString("messages.help-title");
        this.helpFooter = getColoredString("messages.help-footer");
        this.helpCommands = this.config.getStringList("messages.help-commands").stream()
                .map(s -> me.allync.blockregen.util.ColorUtil.color(s))
                .collect(Collectors.toList());

        try {
            this.wandMaterial = Material.valueOf(this.config.getString("wand.material", "WOODEN_AXE").toUpperCase());
        } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("Invalid wand material specified in config.yml. Defaulting to WOODEN_AXE.");
            this.wandMaterial = Material.WOODEN_AXE;
        }
        this.wandName = getColoredString("wand.name");
        this.defaultBreakSound = this.config.getString("sounds.break-sound", "BLOCK_STONE_BREAK:1.0:1.0");
        this.defaultRegenSound = this.config.getString("sounds.regen-sound", "ENTITY_EXPERIENCE_ORB_PICKUP:1.0:1.5");
        this.wrongToolSound = this.config.getString("sounds.wrong-tool-sound", "BLOCK_ANVIL_LAND:0.8:1.0");
        this.allWorldsEnabled = this.config.getBoolean("worlds.enable-all-worlds", false);
        this.enabledWorlds = this.config.getStringList("worlds.enabled-worlds");
        this.sendRegenCountdown = this.config.getBoolean("options.send-regen-countdown", true);
        this.checkForUpdates = this.config.getBoolean("options.check-for-updates", true);
        this.preventMiningWhenFull = this.config.getBoolean("options.prevent-mining-when-full", true);
        this.miningHoldMineTimeoutMs = this.config.getLong("options.mining.hold-timeout-ms", 1800L);
        this.miningReleaseGraceMs = this.config.getLong("options.mining.release-grace-ms", 2000L);
        this.miningResumeTimeoutMs = this.config.getLong("options.mining.resume-timeout-ms", 3000L);
        this.miningDefaultTouchLimit = this.config.getInt("options.mining.default-touch-limit", 2);
        this.worldGuardEnabled = this.config.getBoolean("worldguard.enabled", false);
        this.worldGuardDisableOtherBreak = this.config.getBoolean("worldguard.disable-other-break", false);
        this.worldGuardBreakRegenInDenyRegions = this.config.getBoolean("worldguard.break-regen-in-deny-regions", false);
        this.mmocoreEnabled = this.config.getBoolean("mmocore.enabled", true);
        this.mmocoreHologramOffsetX = this.config.getDouble("mmocore.hologram-offset.x", 0.5);
        this.mmocoreHologramOffsetY = this.config.getDouble("mmocore.hologram-offset.y", 0.8);
        this.mmocoreHologramOffsetZ = this.config.getDouble("mmocore.hologram-offset.z", 0.5);
        this.particlesEnabled = this.config.getBoolean("particles.enabled", true);
        this.particlesOnBreak = this.config.getBoolean("particles.on-break", true);
        this.particlesOnRegen = this.config.getBoolean("particles.on-regen", true);
        this.defaultBreakParticle = this.config.getString("particles.default-break-particle", "CRIT:10:0.5");
        this.defaultRegenParticle = this.config.getString("particles.default-regen-particle", "VILLAGER_HAPPY:10:0.5");
        this.breakDurationHologramEnabled = this.config.getBoolean("break-duration-hologram.enabled", true);
        this.breakDurationHologramText = this.config.getString("break-duration-hologram.text", "&f%time%");
        this.breakDurationHologramOffsetX = this.config.getDouble("break-duration-hologram.offset.x", 0.5);
        this.breakDurationHologramOffsetY = this.config.getDouble("break-duration-hologram.offset.y", 1.3);
        this.breakDurationHologramOffsetZ = this.config.getDouble("break-duration-hologram.offset.z", 0.5);

        // Muat pemetaan profil multiplier dunia
        this.worldMultiplierProfiles = new HashMap<>();
        ConfigurationSection profileSection = config.getConfigurationSection("worlds.world-multiplier-profiles");
        if (profileSection != null) {
            for (String worldName : profileSection.getKeys(false)) {
                worldMultiplierProfiles.put(worldName.toLowerCase(), profileSection.getString(worldName));
            }
        }
    }

    private String getColoredString(String path) {
        String str = this.config.getString(path, "");
        return me.allync.blockregen.util.ColorUtil.color(str);
    }

    /**
     * Mendapatkan nama profil multiplier untuk dunia tertentu.
     * @param worldName Nama dunia.
     * @return Nama profil, atau "default" jika tidak ada yang spesifik.
     */
    public String getMultiplierProfileForWorld(String worldName) {
        return worldMultiplierProfiles.getOrDefault(worldName.toLowerCase(), "default");
    }
}
