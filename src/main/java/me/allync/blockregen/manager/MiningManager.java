package me.allync.blockregen.manager;

import dev.lone.itemsadder.api.CustomBlock;
import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.BlockData;
import me.allync.blockregen.data.CustomDrop;
import me.allync.blockregen.data.ToolRequirement;
import me.allync.blockregen.util.ItemUtil;
import me.allync.blockregen.util.NexoUtil;
import me.allync.blockregen.util.ParticleUtil;
import me.allync.blockregen.util.SoundUtil;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.PlayerProfessions;
import net.Indyuce.mmocore.experience.Profession;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
// Hapus ConcurrentHashMap
// import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the logic for block breaking (drops, sounds, particles, regen)
 * and tracks players actively mining custom-duration blocks.
 */
public class MiningManager {

    private final BlockRegen plugin;
    private final Logger logger;
    private final Set<String> activeMiningLocations = ConcurrentHashMap.newKeySet();

    // --- HAPUS SEMUA MAP LAMA ---
    // private final Map<UUID, Long> playerBreakTime = new ConcurrentHashMap<>();
    // private final Map<UUID, Location> playerMiningLocation = new ConcurrentHashMap<>();
    // private final Map<UUID, Long> playerLastHitTime = new ConcurrentHashMap<>();
    // --- AKHIR PENGHAPUSAN ---

    public MiningManager(BlockRegen plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Processes the actual breaking of a regen block.
     * This is called by BlockBreakListener (for normal breaks)
     * or BlockMiningListener (for custom duration breaks).
     */
    public void processBlockBreak(Player player, Block block, BlockData data, BlockState originalState, String blockIdentifier) {
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            return;
        }

        String currentIdentifier = getBlockIdentifier(block);
        if (currentIdentifier == null || !currentIdentifier.equalsIgnoreCase(blockIdentifier)) {
            return;
        }

        // 1. Handle Drops (Custom & Natural)
        handleAllDrops(player, block, data, blockIdentifier);

        // 2. Execute Commands
        executeCommands(player, data);

        // 3. Handle Vanilla Experience
        String expAmountStr = data.getExpDropAmount();
        int expToDrop = 0;
        if (expAmountStr != null && !expAmountStr.isEmpty()) {
            expToDrop = parseAmount(expAmountStr);
        }

        if (expToDrop > 0) {
            if (data.isAutoPickupExp()) {
                player.giveExp(expToDrop);
            } else {
                // Drop orb at block location
                Location loc = block.getLocation().add(0.5, 0.5, 0.5);

                final int finalExpToDrop = expToDrop;
                block.getWorld().spawn(loc, ExperienceOrb.class, orb -> {
                    orb.setExperience(finalExpToDrop);
                });
            }
        }

        // 4. Handle MMOCore Experience
        handleMMOCoreExp(player, block, data, blockIdentifier);

        // 4.5 Handle AuraSkills Experience
        handleAuraSkillsExp(player, data, blockIdentifier);

        // 5. Play Sound & Spawn Particles
        SoundUtil.playSoundToPlayer(player, block.getLocation(), data.getBreakSound(), plugin.getConfigManager().defaultBreakSound);
        if (plugin.getConfigManager().particlesEnabled && plugin.getConfigManager().particlesOnBreak) {
            String particle = data.getBreakParticle() != null ? data.getBreakParticle() : plugin.getConfigManager().defaultBreakParticle;
            ParticleUtil.spawnParticle(block.getLocation(), particle);
        }

        // 6. Apply replaced block now and register regen/cooldown immediately.
        final String regenVariantIdentifier = selectWeightedRegenBlock(data, blockIdentifier);
        boolean managedRandomPoint = plugin.getRandomOreManager().isEnabled() &&
                plugin.getRandomOreManager().isManagedPoint(block.getLocation());

        block.setType(data.getReplacedBlock());
        if (managedRandomPoint) {
            BlockState cooldownState = block.getState();
            Location brokenLocation = block.getLocation();
            plugin.getRegenManager().startRelocationCooldown(cooldownState, data.getRegenDelay(),
                    () -> plugin.getRandomOreManager().onPointRegenerated(brokenLocation));
        } else {
            plugin.getRegenManager().startRegen(originalState, data.getRegenDelay(), blockIdentifier, regenVariantIdentifier);
        }

        // 7. Send Action Bar Countdown
        if (plugin.getConfigManager().sendRegenCountdown) {
            plugin.getRegenManager().sendActionBarMessage(player, data.getRegenDelay());
        }
    }

    private void handleAllDrops(Player player, Block block, BlockData data, String blockIdentifier) {
        List<ItemStack> finalDrops = new ArrayList<>();
        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);

        double regenMultiplier = 1.0;
        if (plugin.getMultiplierManager().isAnyProfileEnabled()) {
            regenMultiplier = plugin.getPlayerManager().getMultiplierValue(player);
        }

        // Handle Custom Drops
        if (data.hasCustomDrops()) {
            if (data.isSingleCustomDropRoll()) {
                CustomDrop selectedDrop = selectWeightedCustomDrop(data.getCustomDrops().values());
                if (selectedDrop != null) {
                    processCustomDrop(player, selectedDrop, finalDrops, fortuneLevel, regenMultiplier, blockIdentifier);
                }
            } else {
                for (CustomDrop customDrop : data.getCustomDrops().values()) {
                    if (ThreadLocalRandom.current().nextDouble(100.0) < customDrop.getChance()) {
                        processCustomDrop(player, customDrop, finalDrops, fortuneLevel, regenMultiplier, blockIdentifier);
                    }
                }
            }
        }

        // Handle Natural Drops
        if (data.isNaturalDrop()) {
            Collection<ItemStack> naturalDrops = block.getDrops(tool);
            // Apply vanilla fortune config
            BlockData.FortuneData fortuneData = data.getFortuneData();

            for (ItemStack drop : naturalDrops) {
                int amount = drop.getAmount();

                // Apply vanilla fortune multiplier from config
                if (fortuneData != null && fortuneData.isEnabled() && fortuneLevel > 0) {
                    Map<Integer, Double> multipliers = fortuneData.getMultipliers();
                    if (multipliers.containsKey(fortuneLevel)) {
                        double multiplier = multipliers.get(fortuneLevel);
                        amount = (int) Math.round(amount * multiplier);
                        debug(player, blockIdentifier,"Applied Vanilla Fortune " + fortuneLevel + " multiplier of " + multiplier + ". New amount: " + amount);
                    }
                }

                // Apply regen multiplier
                if (regenMultiplier > 1.0) {
                    amount = (int) Math.round(amount * regenMultiplier);
                    debug(player, blockIdentifier,"Applied Regen Multiplier of x" + regenMultiplier + " to natural drop. New amount: " + amount);
                }
                if (amount > 0) {
                    ItemStack finalDrop = drop.clone();
                    finalDrop.setAmount(amount);
                    finalDrops.add(finalDrop);
                }
            }
        }

        if (finalDrops.isEmpty()) {
            return;
        }

        // Give items to player
        if (data.isAutoInventory()) {
            PlayerInventory inventory = player.getInventory();
            HashMap<Integer, ItemStack> remaining = inventory.addItem(finalDrops.toArray(new ItemStack[0]));
            if (!remaining.isEmpty()) {
                remaining.values().forEach(i -> block.getWorld().dropItemNaturally(block.getLocation(), i));
            }
        } else {
            finalDrops.forEach(i -> block.getWorld().dropItemNaturally(block.getLocation(), i));
        }
    }


    private void executeCommands(Player player, BlockData data) {
        if (data.getCommands() == null || data.getCommands().isEmpty()) return;
        executeCommandList(player, data.getCommands());
    }

    private void processCustomDrop(Player player, CustomDrop customDrop, List<ItemStack> finalDrops, int fortuneLevel,
                                   double regenMultiplier, String blockIdentifier) {
        ItemStack item = ItemUtil.createItemStack(customDrop);
        if (item != null) {
            int amount = parseAmount(customDrop.getAmount());

            if (customDrop.isFortuneEnabled() && fortuneLevel > 0) {
                Map<Integer, Double> multipliers = customDrop.getFortuneMultipliers();
                if (multipliers.containsKey(fortuneLevel)) {
                    double multiplier = multipliers.get(fortuneLevel);
                    amount = (int) Math.round(amount * multiplier);
                    debug(player, blockIdentifier, "Applied Custom Drop Fortune " + fortuneLevel + " multiplier of " + multiplier + ". New amount: " + amount);
                }
            }

            if (regenMultiplier > 1.0) {
                amount = (int) Math.round(amount * regenMultiplier);
                debug(player, blockIdentifier, "Applied Regen Multiplier of x" + regenMultiplier + ". New amount: " + amount);
            }

            if (amount > 0) {
                item.setAmount(amount);
                finalDrops.add(item);
            }
        }

        if (customDrop.hasCommands()) {
            executeCommandList(player, customDrop.getCommands());
        }
    }

    private CustomDrop selectWeightedCustomDrop(Collection<CustomDrop> drops) {
        if (drops == null || drops.isEmpty()) {
            return null;
        }

        double totalWeight = 0.0;
        for (CustomDrop drop : drops) {
            if (drop.getChance() > 0) {
                totalWeight += drop.getChance();
            }
        }

        if (totalWeight <= 0) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0;
        for (CustomDrop drop : drops) {
            if (drop.getChance() <= 0) {
                continue;
            }
            cumulative += drop.getChance();
            if (roll < cumulative) {
                return drop;
            }
        }

        return null;
    }

    private String selectWeightedRegenBlock(BlockData data, String defaultBlockIdentifier) {
        if (data == null || !data.hasRegenVariants()) {
            return null;
        }

        double totalWeight = 0.0;
        for (BlockData.RegenVariant variant : data.getRegenVariants()) {
            if (variant.getChance() > 0) {
                totalWeight += variant.getChance();
            }
        }

        if (totalWeight <= 0.0) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0;
        for (BlockData.RegenVariant variant : data.getRegenVariants()) {
            if (variant.getChance() <= 0) {
                continue;
            }
            cumulative += variant.getChance();
            if (roll < cumulative) {
                return variant.getBlockIdentifier();
            }
        }

        return defaultBlockIdentifier;
    }

    private void executeCommandList(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;

        for (String command : commands) {
            String formattedCmd = command.replace("%player%", player.getName());
            if (formattedCmd.startsWith("[CONSOLE]")) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), formattedCmd.substring(9).trim());
            } else if (formattedCmd.startsWith("[PLAYER]")) {
                player.performCommand(formattedCmd.substring(8).trim());
            } else if (formattedCmd.startsWith("[OP]")) {
                boolean wasOp = player.isOp();
                try {
                    if (!wasOp) player.setOp(true);
                    player.performCommand(formattedCmd.substring(4).trim());
                } finally {
                    if (!wasOp) player.setOp(false);
                }
            } else {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), formattedCmd);
            }
        }
    }

    private void handleMMOCoreExp(Player player, Block block, BlockData data, String blockIdentifier) {
        if (!BlockRegen.mmocoreEnabled || !plugin.getConfigManager().mmocoreEnabled) {
            return;
        }
        if (!data.hasMmocoreExp()) {
            return;
        }

        debug(player, blockIdentifier, "Handling MMOCore EXP drops...");

        PlayerData playerData = PlayerData.get(player);
        PlayerProfessions professions = playerData.getCollectionSkills();

        for (Map.Entry<String, Integer> entry : data.getMmocoreExp().entrySet()) {
            String professionId = entry.getKey();
            int expAmount = entry.getValue();

            Profession profession = MMOCore.plugin.professionManager.get(professionId);

            if (profession != null) {
                professions.giveExperience(profession, expAmount, EXPSource.SOURCE,
                        block.getLocation().add(
                                plugin.getConfigManager().mmocoreHologramOffsetX,
                                plugin.getConfigManager().mmocoreHologramOffsetY,
                                plugin.getConfigManager().mmocoreHologramOffsetZ),
                        true);
                debug(player, blockIdentifier, "&aGave &f" + expAmount + " &aEXP to profession &f" + professionId);
            } else {
                debug(player, blockIdentifier, "&cCould not find profession with ID '&f" + professionId + "&c' for player " + player.getName());
            }
        }
    }

    private void handleAuraSkillsExp(Player player, BlockData data, String blockIdentifier) {
        if (!BlockRegen.auraSkillsEnabled) {
            return;
        }
        if (!data.hasAuraskillsXp()) {
            return;
        }

        AuraSkillsApi api;
        try {
            api = AuraSkillsApi.get();
        } catch (Exception e) {
            return;
        }

        SkillsUser user = api.getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : data.getAuraskillsXp().entrySet()) {
            String skillName = entry.getKey();
            int expAmount = entry.getValue();

            try {
                Skills skill = Skills.valueOf(skillName.toUpperCase());
                user.addSkillXp(skill, expAmount);
                debug(player, blockIdentifier, "&aGave &f" + expAmount + " &aAuraSkills XP to skill &f" + skillName);
            } catch (IllegalArgumentException ex) {
                debug(player, blockIdentifier, "&cInvalid AuraSkills skill '&f" + skillName + "&c'.");
            }
        }
    }

    private int parseAmount(String amountStr) {
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
        } else {
            try {
                return Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
    }

    public long calculateRequiredBreakTimeMs(Player player, BlockData data) {
        double baseSeconds = Math.max(0.05D, data.getBreakDuration());
        if (data.isFixedDuration()) {
            return (long) (baseSeconds * 1000.0D);
        }

        double speedMultiplier = calculateBreakSpeedMultiplier(player);
        double effectiveSeconds = Math.max(0.05D, baseSeconds / speedMultiplier);
        return (long) (effectiveSeconds * 1000.0D);
    }

    private double calculateBreakSpeedMultiplier(Player player) {
        double speedMultiplier = 1.0D;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        int efficiencyLevel = mainHand.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiencyLevel > 0) {
            speedMultiplier += efficiencyLevel * 0.15D;
        }

        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        if (haste != null) {
            speedMultiplier += (haste.getAmplifier() + 1) * 0.20D;
        }

        PotionEffect fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (fatigue != null) {
            double penalty = 1.0D + (fatigue.getAmplifier() + 1) * 0.35D;
            speedMultiplier = speedMultiplier / penalty;
        }

        double mmoItemsMiningBonus = getMmoItemsMiningBonus(mainHand);
        if (mmoItemsMiningBonus > 0.0D) {
            speedMultiplier += mmoItemsMiningBonus;
        }

        // Plugin stat hook: reuse BlockRegen multiplier profile as an extra mining speed factor.
        if (plugin.getMultiplierManager().isAnyProfileEnabled()) {
            double profileMultiplier = plugin.getPlayerManager().getMultiplierValue(player);
            if (profileMultiplier > 1.0D) {
                speedMultiplier *= profileMultiplier;
            }
        }

        return Math.max(0.1D, Math.min(speedMultiplier, 20.0D));
    }

    private double getMmoItemsMiningBonus(ItemStack item) {
        if (!BlockRegen.mmoItemsEnabled || item == null || item.getType().isAir()) {
            return 0.0D;
        }

        try {
            LiveMMOItem liveItem = new LiveMMOItem(item);

            // PICKAXE_POWER is the built-in MMOItems mining stat.
            double pickaxePower = readMmoItemsDoubleStat(liveItem, ItemStats.PICKAXE_POWER);
            double bonus = Math.max(0.0D, pickaxePower / 100.0D);

            // Optional custom stat aliases used by some MMOItems setups.
            bonus += readCustomMmoItemsStatBonus(liveItem, "MINING_EFFICIENCY");
            bonus += readCustomMmoItemsStatBonus(liveItem, "MINING_SPEED");
            return bonus;
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private double readCustomMmoItemsStatBonus(LiveMMOItem liveItem, String statId) {
        ItemStat<?, ?> stat = MMOItems.plugin.getStats().get(statId);
        if (stat == null) {
            return 0.0D;
        }
        double value = readMmoItemsDoubleStat(liveItem, stat);
        return Math.max(0.0D, value / 100.0D);
    }

    private double readMmoItemsDoubleStat(LiveMMOItem liveItem, ItemStat<?, ?> stat) {
        if (liveItem == null || stat == null || !liveItem.hasData(stat)) {
            return 0.0D;
        }

        StatData data = liveItem.getData(stat);
        if (data instanceof DoubleData doubleData) {
            return doubleData.getValue();
        }
        return 0.0D;
    }

    /**
     * Gets the unique identifier for a block (Vanilla material, ItemsAdder ID, or Nexo ID).
     */
    public String getBlockIdentifier(Block block) {
        // Check Nexo first
        if (BlockRegen.nexoEnabled) {
            String nexoId = NexoUtil.getNexoBlockId(block);
            if (nexoId != null) {
                return nexoId;
            }
        }

        // Check ItemsAdder
        if (BlockRegen.itemsAdderEnabled) {
            CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
            if (customBlock != null) {
                return customBlock.getNamespacedID();
            }
        }
        
        // Return vanilla material
        return block.getType().name();
    }

    /**
     * Sends a debug message to a player if they have debugging enabled.
     */
    public void debug(Player player, String blockIdentifier, String message) {
        if (plugin.isPlayerDebugging(player)) {
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                    "&8[&eBlockRegen&8-&6Debug&8] (&b" + blockIdentifier + "&8) &7" + message));
        }
    }

    public String formatRequiredTools(BlockData data) {
        if (data == null || !data.requiresTool()) {
            return "alat yang sesuai";
        }

        List<String> names = new ArrayList<>();
        for (ToolRequirement requirement : data.getRequiredTools()) {
            String displayName = requirement.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                names.add(displayName);
            }
        }

        if (names.isEmpty()) {
            return "alat yang sesuai";
        }
        return String.join(", ", names);
    }

    public void markMining(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        activeMiningLocations.add(location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ());
    }

    public void unmarkMining(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        activeMiningLocations.remove(location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ());
    }

    public boolean isBeingMined(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return activeMiningLocations.contains(location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ());
    }

    // --- HAPUS SEMUA GETTER LAMA ---
    // public Map<UUID, Long> getPlayerBreakTime() { ... }
    // public Map<UUID, Location> getPlayerMiningLocation() { ... }
    // public Map<UUID, Long> getPlayerLastHitTime() { ... }
    // public void clearPlayerData(UUID uuid) { ... }
    // --- AKHIR PENGHAPUSAN ---
}