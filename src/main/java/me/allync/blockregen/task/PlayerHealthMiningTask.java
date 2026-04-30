package me.allync.blockregen.task;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.data.ToolRequirement;
import me.allync.blockregen.manager.MiningManager;
import me.allync.blockregen.util.BlockHealthHologramUtil;
import me.allync.blockregen.util.ItemUtil;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

/**
 * Task untuk mode block-health.
 * Berjalan secara otomatis (per detik) selama pemain melihat blok.
 */
public class PlayerHealthMiningTask extends BukkitRunnable {

    private final BlockRegen plugin;
    private final MiningManager miningManager;
    private final Player player;
    private final Block block;
    private final BlockData data;
    private final String blockIdentifier;
    private final BlockState originalState;
    private final Map<UUID, PlayerHealthMiningTask> activeTasks;
    private final ItemStack initialToolSnapshot;

    private int tickCounter = 0;
    private boolean stateCleared = false;
    private long lastOnTargetMs;

    public PlayerHealthMiningTask(
            BlockRegen plugin,
            Player player,
            Block block,
            BlockData data,
            String blockIdentifier,
            Map<UUID, PlayerHealthMiningTask> activeTasks
    ) {
        this.plugin = plugin;
        this.miningManager = plugin.getMiningManager();
        this.player = player;
        this.block = block;
        this.data = data;
        this.blockIdentifier = blockIdentifier;
        this.originalState = block.getState();
        this.activeTasks = activeTasks;
        this.initialToolSnapshot = normalizeItem(player.getInventory().getItemInMainHand());
        this.lastOnTargetMs = System.currentTimeMillis();

        miningManager.markMining(this.block.getLocation());
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancelTask();
            return;
        }

        // Pengecekan target (harus melihat blok)
        Block target = player.getTargetBlockExact(6);
        boolean isOnTarget = target != null && target.getLocation().equals(block.getLocation());
        long now = System.currentTimeMillis();

        if (!isOnTarget) {
            // Beri grace period (toleransi meleset)
            if (now - lastOnTargetMs >= plugin.getConfigManager().miningReleaseGraceMs) {
                cancelTask();
                return;
            }
            return; // JANGAN lanjut tick jika crosshair tidak di blok
        } else {
            lastOnTargetMs = now;
        }

        if (player.getGameMode() != GameMode.SURVIVAL) {
            cancelTask();
            return;
        }

        if (hasToolChanged()) {
            cancelTask();
            return;
        }

        String currentIdentifier = miningManager.getBlockIdentifier(block);
        if (currentIdentifier == null || !currentIdentifier.equalsIgnoreCase(blockIdentifier)) {
            cancelTask();
            return;
        }

        // Deal damage tiap 20 tick (1 detik).
        // Karena awal tickCounter = 0, damage pertama langsung terjadi.
        if (tickCounter == 0) {
            dealDamage();
        }

        tickCounter++;

        // Animasi swing tiap 10 tick agar terlihat natural
        if (tickCounter % 10 == 0) {
            player.swingMainHand();
        }

        if (tickCounter >= 20) {
            tickCounter = 0;
        }
    }

    private void dealDamage() {
        ItemStack held = player.getInventory().getItemInMainHand();
        double power = ItemUtil.getPickaxePower(held);

        if (data.requiresTool()) {
            boolean toolOk = false;
            for (ToolRequirement req : data.getRequiredTools()) {
                if (req.matches(held)) { toolOk = true; break; }
            }
            if (!toolOk && !(data.requiresPickaxePower() && power >= data.getRequirePickaxePower())) {
                cancelTask();
                return;
            }
        }

        if (data.requiresPickaxePower() && power < data.getRequirePickaxePower()) {
            cancelTask();
            return;
        }

        double damage = (power > 0) ? power : plugin.getConfigManager().blockHealthDefaultDamage;
        double maxHp = data.getBlockHealth();
        double currentHp = plugin.getBlockHealthManager().damage(block.getLocation(), maxHp, damage);

        float crackProgress = (float) ((maxHp - currentHp) / maxHp);
        for (Player p : block.getWorld().getPlayers()) {
            p.sendBlockDamage(block.getLocation(), crackProgress);
        }

        BlockHealthHologramUtil.update(block.getLocation(), currentHp, maxHp, plugin);

        SoundUtil.playSoundToPlayer(player, block.getLocation(),
                data.getBreakSound(), plugin.getConfigManager().defaultBreakSound);

        miningManager.debug(player, blockIdentifier,
                "&7[Health] HP: &f" + currentHp + " &7/ &f" + maxHp + " &7(dmg: &c-" + damage + "&7)");

        if (currentHp <= 0) {
            for (Player p : block.getWorld().getPlayers()) {
                p.sendBlockDamage(block.getLocation(), 0.0f);
            }
            BlockHealthHologramUtil.remove(block.getLocation());
            plugin.getBlockHealthManager().reset(block.getLocation());
            miningManager.processBlockBreak(player, block, data, originalState, blockIdentifier);
            clearTaskState();
        }
    }

    public void cancelTask() {
        for (Player p : block.getWorld().getPlayers()) {
            p.sendBlockDamage(block.getLocation(), 0.0f);
        }
        clearTaskState();
    }

    private void clearTaskState() {
        if (stateCleared) return;
        stateCleared = true;
        if (!this.isCancelled()) this.cancel();
        activeTasks.remove(player.getUniqueId());
        miningManager.unmarkMining(block.getLocation());
    }

    private boolean hasToolChanged() {
        ItemStack currentTool = normalizeItem(player.getInventory().getItemInMainHand());
        if (initialToolSnapshot == null && currentTool == null) return false;
        if (initialToolSnapshot == null || currentTool == null) return true;
        return !initialToolSnapshot.isSimilar(currentTool);
    }

    private ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return normalized;
    }

    public Block getBlock() {
        return block;
    }
}
