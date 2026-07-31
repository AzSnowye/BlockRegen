package me.allync.blockregen.manager;

import me.allync.blockregen.BlockRegen;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.entity.Player;

public class PlayerManager {

    private final BlockRegen plugin;
    private final File playerDataFolder;

    public PlayerManager(BlockRegen plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    public void loadPlayerData(Player player) {
    }

    public void savePlayerData(Player player) {
    }

    public void unloadPlayerData(Player player) {
    }
}
