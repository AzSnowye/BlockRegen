package me.allync.blockregen.task;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.manager.RegenManager;
import me.allync.blockregen.util.NexoUtil;
import me.allync.blockregen.util.ParticleUtil;
import me.allync.blockregen.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitRunnable;

public class RegenTask extends BukkitRunnable {
    private final BlockRegen plugin;

    private final RegenManager regenManager;

    private final BlockState originalState;

    private final String blockIdentifier;

    private final String regenVariantIdentifier;

    public RegenTask(BlockRegen plugin, RegenManager regenManager, BlockState originalState, String blockIdentifier, String regenVariantIdentifier) {
        this.plugin = plugin;
        this.regenManager = regenManager;
        this.originalState = originalState;
        this.blockIdentifier = blockIdentifier;
        this.regenVariantIdentifier = regenVariantIdentifier;
    }

    public void run() {
        if (!applyRegenVariant()) {
            this.originalState.update(true, false);
        }
        this.regenManager.removeRegenerating(this.originalState.getLocation());
        this.plugin.getRandomOreManager().onPointRegenerated(this.originalState.getLocation());
        BlockData data = this.plugin.getBlockManager().getBlockData(this.blockIdentifier);
        String sound = (data != null) ? data.getRegenSound() : null;
        SoundUtil.playSound(this.originalState.getLocation(), sound, (this.plugin.getConfigManager()).defaultRegenSound);
        if ((this.plugin.getConfigManager()).particlesEnabled && (this.plugin.getConfigManager()).particlesOnRegen) {
            String particle = (data != null && data.getRegenParticle() != null) ? data.getRegenParticle() : (this.plugin.getConfigManager()).defaultRegenParticle;
            ParticleUtil.spawnParticle(this.originalState.getLocation(), particle);
        }
    }

    private boolean applyRegenVariant() {
        if (regenVariantIdentifier == null || regenVariantIdentifier.isEmpty()) {
            return false;
        }

        Block block = this.originalState.getLocation().getBlock();

        if (regenVariantIdentifier.toLowerCase().startsWith("nexo:")) {
            return BlockRegen.nexoEnabled && NexoUtil.placeNexoBlock(regenVariantIdentifier, this.originalState.getLocation());
        }

        if (regenVariantIdentifier.contains(":")) {
            // Unknown namespaced block provider (not Nexo); fall back to original state.
            return false;
        }

        try {
            Material material = Material.valueOf(regenVariantIdentifier.toUpperCase());
            block.setType(material, false);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
