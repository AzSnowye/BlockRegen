package me.allync.blockregen.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RegionManager {

    private final BlockRegen plugin;
    private final File regionsFile;
    private FileConfiguration regionsConfig;

    private final List<Region> regions = new ArrayList<>();
    private final Map<UUID, Location> pos1Selections = new HashMap<>();
    private final Map<UUID, Location> pos2Selections = new HashMap<>();

    public RegionManager(BlockRegen plugin) {
        this.plugin = plugin;
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            plugin.saveResource("regions.yml", false);
        }
        this.regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
    }

    public void loadRegions() {
        regions.clear();
        ConfigurationSection regionsSection = regionsConfig.getConfigurationSection("regions");
        if (regionsSection == null) return;

        for (String key : regionsSection.getKeys(false)) {
            World world = Bukkit.getWorld(regionsSection.getString(key + ".world"));
            if (world == null) {
                plugin.getLogger().warning("World not found for region '" + key + "'. Skipping...");
                continue;
            }
            Location pos1 = regionsSection.getVector(key + ".pos1").toLocation(world);
            Location pos2 = regionsSection.getVector(key + ".pos2").toLocation(world);
            regions.add(new Region(key, pos1, pos2));
        }
        plugin.getLogger().info("Loaded " + regions.size() + " regions.");
    }

    public void saveRegion(Player player, String name) throws IOException {
        Location pos1 = pos1Selections.get(player.getUniqueId());
        Location pos2 = pos2Selections.get(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            player.sendMessage(plugin.getConfigManager().prefix + "You must set both positions first.");
            return;
        }

        String path = "regions." + name;
        regionsConfig.set(path + ".world", pos1.getWorld().getName());
        regionsConfig.set(path + ".pos1", pos1.toVector());
        regionsConfig.set(path + ".pos2", pos2.toVector());
        regionsConfig.save(regionsFile);

        // Add to loaded regions and clear selections
        regions.add(new Region(name, pos1, pos2));
        pos1Selections.remove(player.getUniqueId());
        pos2Selections.remove(player.getUniqueId());
    }

    public boolean removeRegion(String name) throws IOException {
        Region regionToRemove = null;
        for (Region region : regions) {
            if (region.getName().equalsIgnoreCase(name)) {
                regionToRemove = region;
                break;
            }
        }

        if (regionToRemove != null) {
            regions.remove(regionToRemove);
            regionsConfig.set("regions." + name, null); // Remove from config
            regionsConfig.save(regionsFile);
            return true;
        }
        return false;
    }

    public boolean isLocationInRegion(Location location) {
        // If no regions have been defined, treat regions as unrestricted.
        // Regen is allowed everywhere in the configured worlds.
        if (regions.isEmpty()) {
            return true;
        }
        for (Region region : regions) {
            if (region.contains(location)) {
                return true;
            }
        }
        return false;
    }

    public Region getRegionAt(Location location) {
        if (location == null || regions.isEmpty()) {
            return null;
        }
        for (Region region : regions) {
            if (region.contains(location)) {
                return region;
            }
        }
        return null;
    }

    public String getRegionNameAt(Location location) {
        Region region = getRegionAt(location);
        return region != null ? region.getName() : null;
    }

    public Set<String> getRegionNamesAt(Location location) {
        Set<String> names = new HashSet<>();
        if (location == null || location.getWorld() == null) {
            return names;
        }

        // Internal BlockRegen regions
        Region internal = getRegionAt(location);
        if (internal != null && internal.getName() != null) {
            names.add(internal.getName().toLowerCase());
        }

        // WorldGuard regions
        if (plugin.getConfigManager().worldGuardEnabled && plugin.getWorldGuardPlugin() != null) {
            try {
                com.sk89q.worldguard.protection.managers.RegionManager wgRegionManager =
                        WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(location.getWorld()));
                if (wgRegionManager != null) {
                    BlockVector3 position = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                    for (ProtectedRegion region : wgRegionManager.getApplicableRegions(position)) {
                        if (region.getId() != null && !region.getId().isEmpty()) {
                            names.add(region.getId().toLowerCase());
                        }
                    }
                }
            } catch (Exception ignored) {
                // Keep internal region behavior if WG query fails.
            }
        }

        return names;
    }

    public boolean isLocationInAnySupportedRegion(Location location) {
        // If no BlockRegen regions are defined, behave as unrestricted (WorldGuard flags still apply separately).
        if (regions.isEmpty() && (plugin.getWorldGuardPlugin() == null || !plugin.getConfigManager().worldGuardEnabled)) {
            return true;
        }
        return isLocationInRegion(location);
    }

    public void setPos1(Player player, Location location) {
        pos1Selections.put(player.getUniqueId(), location);
    }

    public void setPos2(Player player, Location location) {
        pos2Selections.put(player.getUniqueId(), location);
    }

    public Location getPos1(Player player) {
        return pos1Selections.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2Selections.get(player.getUniqueId());
    }

    public List<String> getRegionNames() {
        List<String> names = new ArrayList<>();
        for (Region region : regions) {
            names.add(region.getName());
        }
        return names;
    }
}
