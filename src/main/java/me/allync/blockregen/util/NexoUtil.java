package me.allync.blockregen.util;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoItems;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class NexoUtil {

    private static final Logger LOGGER = Logger.getLogger("BlockRegen");

    private NexoUtil() {
        // Utility class
    }

    public static boolean isNexoItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            return NexoItems.exists(itemStack);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isNexoBlock(Block block) {
        if (block == null) {
            return false;
        }
        try {
            return NexoBlocks.isCustomBlock(block);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String getNexoItemId(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        try {
            String itemId = NexoItems.idFromItem(itemStack);
            return itemId == null || itemId.isEmpty() ? null : "nexo:" + itemId;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error getting Nexo item ID", e);
            return null;
        }
    }

    public static String getNexoBlockId(Block block) {
        if (block == null) {
            return null;
        }
        try {
            if (NexoBlocks.customBlockMechanic(block) == null) {
                return null;
            }
            String blockId = NexoBlocks.customBlockMechanic(block).getItemID();
            return blockId == null || blockId.isEmpty() ? null : "nexo:" + blockId;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error getting Nexo block ID", e);
            return null;
        }
    }

    public static ItemStack createNexoItemStack(String nexoItemId) {
        if (nexoItemId == null || nexoItemId.isEmpty()) {
            return null;
        }

        String itemId = nexoItemId.startsWith("nexo:") ? nexoItemId.substring(5) : nexoItemId;

        try {
            if (!NexoItems.exists(itemId)) {
                return null;
            }
            return NexoItems.itemFromId(itemId).build();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create Nexo item: " + nexoItemId, e);
            return null;
        }
    }

    public static boolean placeNexoBlock(String nexoBlockId, Location location) {
        if (nexoBlockId == null || nexoBlockId.isEmpty() || location == null) {
            return false;
        }

        String blockId = nexoBlockId.startsWith("nexo:") ? nexoBlockId.substring(5) : nexoBlockId;

        try {
            if (!NexoBlocks.isCustomBlock(blockId)) {
                return false;
            }
            NexoBlocks.place(blockId, location);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to place Nexo block: " + nexoBlockId, e);
            return false;
        }
    }

    public static boolean removeNexoBlock(Location location) {
        if (location == null) {
            return false;
        }

        try {
            if (!NexoBlocks.isCustomBlock(location.getBlock())) {
                return false;
            }
            return NexoBlocks.remove(location);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to remove Nexo block at " + location, e);
            return false;
        }
    }

    public static boolean isNexoReady() {
        try {
            return NexoItems.itemNames() != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
